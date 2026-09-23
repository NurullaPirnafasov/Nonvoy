# Nonvoy Bot

Mahalla novvoyxonasi uchun Telegram buyurtma boti. Mijoz botdan non buyurtma qiladi,
to'lov chekini yuboradi, tayyor bo'lganda xabar oladi va **o'zi olib ketadi**.

## Nega shunday qilingan

Novvoyxona bitta, mahsulot turlari sanoqli, mijozlar mahalladan. Shu sababli loyihada
ataylab yo'q narsalar bor:

- **Yetkazib berish yo'q** — hamma o'zi olib ketadi, ya'ni manzil, kuryer, yetkazish narxi kerak emas.
- **Onlayn to'lov integratsiyasi yo'q** — mijoz summani kartaga o'tkazib chek rasmini yuboradi,
  kassa ko'z bilan tekshiradi. Click/Payme bitta novvoyxona uchun ortiqcha.
- **Admin panel yo'q** — novvoy alohida sayt ochib o'tirmaydi. Buyurtmalar Telegram guruhga
  karta ko'rinishida tushadi, status shu yerdagi tugmalar bilan boshqariladi.
- **Redis yo'q** — foydalanuvchi flow'ning qaysi qadamida ekani `users.state` ustunida saqlanadi.
  Bitta novvoyxona yuki uchun DB yetarli, yana bitta servis saqlash esa ortiqcha xarajat.
- **"Bugun tugadi" holati yo'q** — non tugasa odam navbatga yoziladi, ketmaydi. Maydon hech qachon
  foydali ish qilmaydi, lekin har ekranda shart-tekshiruv qo'shadi.
- **Ish vaqti tekshiruvi yo'q** — tungi buyurtma ham qabul qilinadi, `NEW` bo'lib navbatda turadi,
  novvoy ertalab ko'radi.

Asosiy g'oya: flow maksimal sodda, ichki struktura toza — keyin feature qo'shish og'riqsiz bo'lsin.

## Stack

- Java 21, Spring Boot 3.3
- PostgreSQL + Spring Data JPA
- [telegrambots](https://github.com/rubenlagus/TelegramBots) 6.9, long polling
- Maven

Arxitektura oddiy layered: `Bot handler → Service → Repository`. Mapper/Event/Integration
qatlamlari yo'q — ular bu hajmdagi loyihada faqat fayl sonini oshiradi.

## Domen

| Entity | Nima uchun |
|---|---|
| `User` | telegramId, telefon, rol, flow state, qoralama (tanlangan mahsulot va chek) |
| `Product` | nomi, narxi |
| `CartItem` | tugallanmagan buyurtma qoralamasi, `(user_id, product_id)` unikal |
| `Order` | egasi, status, jami summa, chek rasmining `file_id`'si |
| `OrderItem` | miqdor, **`priceAtOrder`**, **`productNameAtOrder`** |

Bir nechta qaror alohida izohga arziydi:

- **Savat alohida entity'da** — buyurtma faqat oxirgi tasdiqdan keyin yaratiladi, shunda bazada
  chala `Order` hech qachon qolmaydi va "yakunlanmagan" status kerak emas.
- **`CartItem`da narx yo'q** — savatda joriy narx ko'rsatiladi, muzlatish esa `OrderItem`da.
  Savat ochiq turganda narx o'zgarsa, mijoz ekranidagi son yangilanadi, bazaga nomuvofiqlik kirmaydi.
- **`priceAtOrder` va `productNameAtOrder`** — buyurtma paytidagi narx va nom muzlatiladi.
  Mahsulot keyin qayta nomlansa yoki o'chirilsa ham eski buyurtma kartasi to'liq chiqadi.
- **Buyurtma raqami sifatida `id`** — alohida `orderNumber` hisoblagichi yo'q. `id` unikal,
  sequence'da uzilish bo'lishi mumkin, lekin novvoyxona uchun raqam shunchaki identifikator.

### Status flow

```
NEW → ACCEPTED → READY
  ↘ CANCELLED (NEW yoki ACCEPTED holatidan)
```

Ikki guruhli tartibda status'lar shu ma'noni oladi (yangi status qo'shilmagan):

- `NEW` — chek yuborildi, kassa to'lovni tekshirmoqda
- `ACCEPTED` — to'lov tasdiqlandi, karta ishchilar guruhiga tushdi
- `READY` — non tayyor, mijozga xabar ketdi
- `CANCELLED` — kassa bekor qildi

`COMPLETED` yo'q — olib ketishni kuzatish novvoyga ortiqcha yumush. O'tishlar
`OrderService.changeStatus` ichida validatsiya qilinadi: ruxsatsiz o'tish
`IllegalStateException` beradi, tugmani ikki marta bosgan admin esa "allaqachon
tasdiqlangan" javobini oladi va kartadagi tugmalar haqiqiy holatga moslanadi.

Bekor qilish matni **eski statusdan** chiqadi: `NEW → CANCELLED` "to'lov topilmadi",
`ACCEPTED → CANCELLED` "bekor qilindi". Alohida `REJECTED` status kerak emas.

## Mijoz flow

```
/start → telefon (bir marta)
  → "Buyurtma berish" → mahsulot tanlash → miqdor
  → savat: [➕ Yana] [✅ To'g'ri] [❌ Bekor]
  → kartaga o'tkazish + chek rasmi
  → chek va savat ko'rsatiladi: [✅ Tasdiqlash] [❌ Bekor]
  → Buyurtma #47 yaratiladi
```

- Bir xil mahsulot ikkinchi marta qo'shilsa yangi qator emas, miqdor qo'shiladi.
- Chek — faqat rasm. Albom (bir nechta rasm) rad etiladi, bot bir marta javob beradi.
- Yakuniy tasdiq ekranida yangi rasm eskisini almashtiradi: mijoz aynan shu yerda chekini
  ko'radi va xato yuborganini payqaydi.
- Chek yuborilmasa muammo yo'q — buyurtma umuman yaratilmagan bo'ladi, faqat savat qoladi.
  Timeout yoki tozalovchi job kerak emas.

Har bir state matndan boshqa narsaga ham (rasm, stiker, ovoz) javob beradi va nima
kutilayotganini eslatadi — foydalanuvchi jimlikka qolmaydi. `/start` istalgan qadamda
ishlaydi, savatni tozalaydi va boshlang'ich holatga qaytaradi.

## Novvoy flow — ikkita guruh

Har guruhda bitta oldinga siljituvchi tugma bor.

**1. Kassa guruhi** (`ADMIN_GROUP_ID`) — to'lovni tekshiradi. Karta chek rasmi bilan tushadi:

```
🆕 Buyurtma #47 — to'lov tekshirilmoqda
👤 Alisher (+998901234567)
🍞 Non × 10 — 50 000 so'm
💰 Jami: 50 000 so'm
[✅ To'lov tasdiqlandi] [❌ Bekor]
```

**2. Ishchilar guruhi** (`WORKER_GROUP_ID`) — nima yopish kerakligini ko'radi. Chek ham,
telefon ham, narx ham yo'q:

```
✅ Buyurtma #47 — tayyorlash kerak
🍞 Non × 10
[🍞 Tayyor]
```

Bekor qilish faqat kassada: pul kassada olingan, shuning uchun pulga bog'liq qaror bitta
joyda turadi. Kassa `ACCEPTED`ni bekor qilsa ishchilarga alohida "yopmang" xabari ketadi —
aks holda non allaqachon tandirda bo'ladi.

## Mahsulot boshqaruvi

Kassa guruhida `/mahsulotlar` (Telegram buyruq menyusida ham turadi):

```
🍞 Mahsulotlar
[Non — 5 000 so'm]
[Patir — 7 000 so'm]
[➕ Yangi mahsulot]
```

Mahsulot tanlansa o'sha xabar kartaga aylanadi: narxni o'zgartirish, nomini o'zgartirish,
o'chirish, orqaga.

Nom va narx yozish kerak bo'lganda bot savol beradi va admin **o'sha savolga reply** qiladi.
Sababi texnik: BotFather'da privacy mode default yoqilgan va bot guruhda faqat buyruqlarni
hamda o'z xabariga qilingan reply'larni ko'radi — "keyingi matnni ushlayman" degan state
mashinasi u xabarni umuman olmaydi. Natijada admin holati bazada saqlanmaydi, ikki admin
parallel ishlay oladi va bot guruh suhbatiga aralashmaydi.

Kontekst savol matnining oxiridagi quyruqda sayohat qiladi: `[narx #3 @812]` — amal,
mahsulot id'si va ro'yxat xabarining `messageId`'si. Oxirgisi tufayli javob qayta ishlangach
o'sha ro'yxat joyida yangilanadi, guruhda yangi-yangi ro'yxatlar to'planmaydi.

Mahsulot o'chirilganda eski buyurtmalar saqlanib qoladi: `OrderItem`dagi ishora uziladi,
nom va narx u yerda allaqachon muzlatilgan.

## Ishga tushirish

Kerak: JDK 21, PostgreSQL, [@BotFather](https://t.me/BotFather)dan olingan bot tokeni.

1. **Baza yarating:**

   ```sql
   CREATE DATABASE nonvoy;
   ```

2. **Ikkita guruh tayyorlang:** Telegram'da kassa va ishchilar guruhlarini oching, botni
   a'zo qiling va admin qilib qo'ying (inline tugmalar uchun kerak). Guruh id'lari manfiy son
   bo'ladi (masalan `-1001234567890`) — [@getidsbot](https://t.me/getidsbot) orqali bilib olsa bo'ladi.

3. **Environment o'zgaruvchilar:**

   | Nomi | Tavsif |
   |---|---|
   | `BOT_TOKEN` | BotFather bergan token |
   | `BOT_USERNAME` | bot username (`@`siz) |
   | `ADMIN_GROUP_ID` | kassa guruhi chat id |
   | `WORKER_GROUP_ID` | ishchilar guruhi chat id |
   | `PAYMENT_CARD` | to'lov kartasi raqami (default: dummy) |
   | `PAYMENT_CARD_HOLDER` | karta egasining ismi (default: dummy) |
   | `DB_USERNAME` | ixtiyoriy, default `postgres` |
   | `DB_PASSWORD` | ixtiyoriy, default `postgres` |

   ```bash
   export BOT_TOKEN=123456:AA...
   export BOT_USERNAME=nonvoy_bot
   export ADMIN_GROUP_ID=-1001234567890
   export WORKER_GROUP_ID=-1009876543210
   export PAYMENT_CARD="8600 1234 5678 9012"
   export PAYMENT_CARD_HOLDER="Ism Familiya"
   ```

4. **Ishga tushiring:**

   ```bash
   ./mvnw spring-boot:run
   ```

Sxema [Flyway](https://flywaydb.org) migratsiyalari bilan yaratiladi
(`src/main/resources/db/migration`), Hibernate esa faqat tekshiradi (`ddl-auto: validate`):
entity bilan baza mos kelmasa dastur ishga tushmaydi. Birinchi ishga tushishda bitta "Non"
mahsuloti seed qilinadi (`ProductSeeder`). Qolganini `/mahsulotlar` orqali qo'shasiz.

Long polling ishlatilgani uchun statik IP yoki HTTPS domen shart emas — bot oddiy uy
kompyuterida ham ishlaydi.

## Testlar

```bash
./mvnw test
```

Testlar DB va Telegram tokenisiz ishlaydi — qamrab olingani biznes qoidalari va sof
funksiyalar: status o'tishlari va savatdan buyurtma yasash (`OrderServiceImplTest`),
savat birlashtirish va jami summa (`CartServiceImplTest`), mahsulot o'chirishda FK tartibi
(`ProductServiceImplTest`), ikki auditoriya uchun karta formatlash, reply quyrug'i va
kiritilgan matn validatsiyasi.

Bot handler'lari va repozitoriylar uchun test yo'q: birinchisi Telegram API'ga,
ikkinchisi haqiqiy bazaga bog'liq. Ular kerak bo'lganda Testcontainers bilan qo'shiladi.

## Rejalashtirilgan

- Savatni tahrirlash (pozitsiyani o'chirish) — hozircha xato bo'lsa bekor qilib boshidan.
- Webhook (hozircha long polling).
- Statistika: kunlik buyurtmalar soni va summasi.
