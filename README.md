# Nonvoy Bot

Mahalla novvoyxonasi uchun Telegram buyurtma boti. Mijoz botdan non buyurtma qiladi,
tayyor bo'lganda xabar oladi va **o'zi olib ketadi**.

## Nega shunday qilingan

Novvoyxona bitta, mahsulot deyarli bitta, mijozlar mahalladan. Shu sababli loyihada
ataylab yo'q narsalar bor:

- **Yetkazib berish yo'q** — hamma o'zi olib ketadi, ya'ni manzil, kuryer, yetkazish narxi kerak emas.
- **Onlayn to'lov yo'q** — pul joyida beriladi. Click/Payme integratsiyasi bitta novvoyxona uchun ortiqcha.
- **Admin panel yo'q** — novvoy alohida sayt ochib o'tirmaydi. Buyurtmalar Telegram guruhga
  karta ko'rinishida tushadi, status shu yerdagi tugmalar bilan boshqariladi.
- **Redis yo'q** — foydalanuvchi flow'ning qaysi qadamida ekani `users.state` ustunida saqlanadi.
  Bitta novvoyxona yuki uchun DB yetarli, yana bitta servis saqlash esa ortiqcha xarajat.
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
| `User` | telegramId, telefon, rol, flow state |
| `Product` | nomi, narxi, `available` ("bugun bor/yo'q") |
| `Order` | egasi, status, jami summa |
| `OrderItem` | mahsulot, miqdor, **`priceAtOrder`** |

Ikki qaror alohida izohga arziydi:

- **`priceAtOrder`** — buyurtma paytidagi narx muzlatiladi. Non narxi ertaga o'zgarsa,
  kechagi buyurtma summasi o'zgarmasligi kerak.
- **Buyurtma raqami sifatida `id`** — alohida `orderNumber` hisoblagichi yo'q. `id` unikal,
  sequence'da uzilish bo'lishi mumkin, lekin novvoyxona uchun raqam shunchaki identifikator.

### Status flow

```
NEW → ACCEPTED → READY
  ↘ CANCELLED (NEW yoki ACCEPTED holatidan)
```

`COMPLETED` yo'q — olib ketishni kuzatish novvoyga ortiqcha yumush. O'tishlar
`OrderService.changeStatus` ichida validatsiya qilinadi: ruxsatsiz o'tish
`IllegalStateException` beradi, tugmani ikki marta bosgan admin esa "allaqachon qabul qilingan"
javobini oladi.

## Mijoz flow

1. `/start` → telefon raqami contact tugmasi orqali so'raladi (bir marta)
2. "Buyurtma berish" → miqdor so'raladi (kamida 1 ta butun son, yuqori chegara yo'q)
3. Tasdiqlash tugmasi → `Buyurtma #47 qabul qilindi ✅ Jami: 10 ta non — 50 000 so'm`
4. Status o'zgarganda avtomatik xabar keladi (`Noningiz tayyor, olib ketishingiz mumkin 🍞`)

Har bir state matndan boshqa narsaga ham (rasm, stiker, ovoz) javob beradi va nima
kutilayotganini eslatadi — foydalanuvchi jimlikka qolmaydi.

## Novvoy (admin) flow

Buyurtma admin guruhga shunday tushadi:

```
🆕 Buyurtma #47
👤 Alisher (+998901234567)
🍞 Non × 10 — 50 000 so'm
[✅ Qabul] [🍞 Tayyor] [❌ Bekor]
```

Tugma bosilganda status o'zgaradi, karta yangi holatga qayta chiziladi va mijozga xabar ketadi.
Yakuniy statusda (`READY`, `CANCELLED`) tugmalar qolmaydi.

`/mahsulotlar` — mahsulotlar ro'yxati, har biri yonida "bor/tugadi" toggle tugmasi.
Non tugagan bo'lsa mijoz buyurtma bera olmaydi.

## Ishga tushirish

Kerak: JDK 21, PostgreSQL, [@BotFather](https://t.me/BotFather)dan olingan bot tokeni.

1. **Baza yarating:**

   ```sql
   CREATE DATABASE nonvoy;
   ```

2. **Admin guruhni tayyorlang:** Telegram'da guruh oching, botni a'zo qiling va admin
   qilib qo'ying (inline tugmalar uchun kerak). Guruh id'si manfiy son bo'ladi
   (masalan `-1001234567890`) — uni [@getidsbot](https://t.me/getidsbot) orqali bilib olsa bo'ladi.

3. **Environment o'zgaruvchilar:**

   | Nomi | Tavsif |
   |---|---|
   | `BOT_TOKEN` | BotFather bergan token |
   | `BOT_USERNAME` | bot username (`@`siz) |
   | `ADMIN_GROUP_ID` | admin guruh chat id |
   | `DB_USERNAME` | ixtiyoriy, default `postgres` |
   | `DB_PASSWORD` | ixtiyoriy, default `postgres` |

   ```bash
   export BOT_TOKEN=123456:AA...
   export BOT_USERNAME=nonvoy_bot
   export ADMIN_GROUP_ID=-1001234567890
   ```

4. **Ishga tushiring:**

   ```bash
   ./mvnw spring-boot:run
   ```

Birinchi ishga tushishda jadvallar avtomatik yaratiladi (`ddl-auto: update`) va bitta
"Non" mahsuloti seed qilinadi (`ProductSeeder`). Narxni `/mahsulotlar` orqali emas,
hozircha bazadan o'zgartiriladi.

Long polling ishlatilgani uchun statik IP yoki HTTPS domen shart emas — bot oddiy uy
kompyuterida ham ishlaydi.

## Rejalashtirilgan

- **Chek orqali to'lov tekshiruvi:** mijoz karta o'tkazmasi chekining rasmini yuboradi,
  rasm buyurtma kartasiga qo'shilib admin guruhga tushadi, novvoy pul o'tganini
  ko'rgach "Qabul" qiladi. To'lov integratsiyasi emas — chek shunchaki rasm.
- Bir nechta mahsulot turi (patir, shirmoy) — flow boshiga bitta tanlash qadami qo'shiladi,
  arxitektura o'zgarmaydi.
- Webhook (hozircha long polling).