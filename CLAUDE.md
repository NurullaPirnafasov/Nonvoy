# Nonvoy Bot — Mahalla novvoyxonasi uchun buyurtma boti

## Loyiha haqida

Mahallada faoliyat yuritadigan bitta novvoyxona uchun Telegram bot. Mijozlar botdan non buyurtma qiladi (asosan ko'p miqdorda, muzlatkichga), tayyor bo'lganda xabar oladi va **o'zlari olib ketadi** (pickup only). Yetkazib berish, onlayn to'lov, multi-tenant — YO'Q.

**Asosiy printsip:** flow maksimal sodda, ichki struktura toza (qatlamlar, enum'lar, narx muzlatish) — shunda keyin feature qo'shish og'riqsiz bo'ladi.

## Ishlash tartibi (MUHIM — Claude uchun qoida)

1. Avval **reja to'liq tuzib bo'linadi** — qadamlar, qarorlar, tuzoqlar muhokama qilinadi. Reja tugamaguncha kod yozilmaydi.
2. Reja tayyor bo'lgach, Nurulla **qaysi qismini yozishni aytadi**.
3. Faqat shundan keyin Claude kod yozadi — aytilgan qismni, undan ortig'ini emas.
4. Dizayn qarorlari asoslanadi: "menimcha" yetarli emas, har bir tanlov uchun sabab aytiladi.

## Texnik stack

- Java 21, Spring Boot 3.3.x, Maven
- PostgreSQL
- telegrambots kutubxonasi, long polling (webhook keyinroq)
- Package: `uz.nonvoy.bot` (yoki mijoz nomiga mos)

## Arxitektura

Oddiy layered: `Bot handler → Service → Repository`. Mapper/Event/Integration layer'lar KERAK EMAS.

### Entity'lar (5 ta, hammasi Auditable'dan meros: createdAt, updatedAt)

- **User** — telegramId, phone (contact orqali), name, role (CUSTOMER/ADMIN), state (bot flow qadami), `draftProductId` (miqdor so'ralayotgan mahsulot), `draftReceiptFileId` (chek rasmining Telegram file_id'si — buyurtma hali yaratilmagani uchun shu yerda kutadi)
- **Product** — nomi, narxi. Yangi turlar (patir, shirmoy) qo'shilishi kutiladi. "Bugun bor/yo'q" holati YO'Q (22-qarorga qarang)
- **CartItem** — user, product, quantity. Tugallanmagan buyurtma qoralamasi; `(user_id, product_id)` unique. Savat ko'rilayotganda narx joriy (4-qaror), summa aytilganda `nameAtCheckout` / `priceAtCheckout`ga muzlatiladi (34-qaror)
- **Order** — user, status, jami summa, chek rasmining file_id'si. Buyurtma raqami sifatida `Auditable.id` ishlatiladi (alohida `orderNumber` maydoni YO'Q — id unique, hisoblagich kerak emas; sequence'da uzilish bo'lishi mumkin, lekin novvoyxona uchun raqam shunchaki identifikator)
- **OrderItem** — order, product, quantity, **priceAtOrder** (buyurtma paytidagi narx muzlatiladi — mahsulot narxi keyin o'zgarsa buyurtma tarixi buzilmasligi uchun). 6-bosqichda `productNameAtOrder` ham qo'shiladi (23-qaror)

### Status flow (sodda)

```
NEW → ACCEPTED → READY
  ↘ CANCELLED (NEW yoki ACCEPTED holatidan)
```

Ikki guruhli tartibda status'lar shu ma'noni oladi (yangi status QO'SHILMAYDI):

- **NEW** — chek yuborildi, kassa guruhida to'lov tekshirilmoqda
- **ACCEPTED** — to'lov tasdiqlandi, karta ishchilar guruhiga tushdi
- **READY** — non tayyor, mijozga xabar ketdi
- **CANCELLED** — kassa bekor qildi

COMPLETED yo'q — olib ketishni kuzatish novvoyga ortiqcha yumush. Status o'tishlari service'da validatsiya qilinadi, noto'g'ri o'tish exception beradi.

### Bot state management

Foydalanuvchi flow'da qaysi qadamda ekani DB'da saqlanadi (`User.state` enum). Redis KERAK EMAS.

## Mijoz flow

1. `/start` → agar telefon yo'q bo'lsa, contact button orqali so'raladi (bir marta)
2. Mahsulot tanlash → miqdorni raqam bilan yozish (validatsiya: kamida 1 ta butun son, yuqori chegara yo'q)
3. Savat ko'rinadi → yana mahsulot qo'shish yoki tasdiqlash
4. To'lov: summani kartaga o'tkazib, chek rasmini yuborish (onlayn to'lov integratsiyasi YO'Q, tekshiruv qo'lda)
5. Yakuniy tasdiq → buyurtma yaratiladi, raqami beriladi, guruhga chek bilan tushadi
6. Status o'zgarganda avtomatik xabar (ayniqsa READY: "Noningiz tayyor, olib ketishingiz mumkin 🍞")
7. Tungi buyurtmalar ham qabul qilinadi — NEW bo'lib navbatda turadi, novvoy ertalab ko'radi. Ish vaqti validatsiyasi YO'Q.

To'liq qadamlar va qarorlar uchun "5-bosqich" bo'limiga qarang.

## Novvoy (admin) flow

Alohida panel YO'Q. Buyurtmalar **ikkita Telegram guruhga** (kanal emas — tugma bosishni boshqarish guruhda qulay) karta ko'rinishida tushadi. Har guruhda bitta oldinga siljituvchi tugma bor:

**1. Kassa (admin) guruhi** — `bot.admin-group-id`. To'lovni tekshiradi. Karta `SendPhoto` (chek rasmi) + caption:

```
🆕 Buyurtma #47 — to'lov tekshirilmoqda
👤 Alisher (+998 9x xxx xx xx)
🍞 Non × 10 — 50 000 so'm
🍞 Patir × 5 — 30 000 so'm
💰 Jami: 80 000 so'm
[✅ To'lov tasdiqlandi] [❌ Bekor]        ← NEW
[❌ Bekor]                                ← ACCEPTED
(tugma yo'q)                              ← READY, CANCELLED
```

**2. Ishchilar guruhi** — `bot.worker-group-id`. Nima yopish kerakligini ko'radi. Karta oddiy `SendMessage` (matn), chek rasmi YO'Q, telefon YO'Q:

```
✅ Buyurtma #47 — tayyorlash kerak
🍞 Non × 10
🍞 Patir × 5
[🍞 Tayyor]                               ← ACCEPTED
(tugma yo'q)                              ← READY, CANCELLED
```

Tugma bosilganda status o'zgaradi va mijozga xabar ketadi. Qabul qilish va bekor qilish — faqat kassa; ishchilar faqat "Tayyor" bosadi.

Admin buyruq: `/mahsulotlar` — mahsulotlarni inline tugmalar orqali boshqarish (kassa guruhida). Batafsil: "6-bosqich".
`/buyurtmalar` — ikkala guruhda, o'sha guruhning ochiq kartalarini qayta chiqaradi. Batafsil: "7-bosqich".

## Reja (kuniga 3-4 soat, jami ~2-2.5 hafta)

### 1-bosqich: Skelet va domain (2 kun)
- [x] Spring Boot loyiha setup (pom.xml, application.yml, PostgreSQL ulanish)
- [x] BotFather'da bot yaratish, long polling, `/start`ga oddiy javob
- [x] 4 ta entity + Auditable + enum'lar + repository'lar
- [x] Status o'tish validatsiyasi (service metod)

### 2-bosqich: Mijoz flow (3-4 kun)
- [x] State management (User.state asosida update routing)
- [x] Ro'yxatdan o'tish (contact button)
- [x] Miqdor so'rash (qo'lda raqam yozish, validatsiya: kamida 1, yuqori chegara yo'q)
- [x] Tasdiqlash + buyurtma yaratish + buyurtma raqami

### 3-bosqich: Admin flow (2-3 kun)
- [x] Buyurtma kartasi admin guruhga yuborish
- [x] Callback tugmalar → status o'zgartirish → mijozga notification
- [x] `/mahsulotlar` boshqaruvi (toggle)

### 4-bosqich: Sayqallash va topshirish (2-3 kun)
- [x] Exception handling, edge case'lar (ikki marta bosish, noto'g'ri raqam, bekor qilingan buyurtmani "Tayyor" qilish)
- [x] **Matn/kontakt bo'lmagan update'ga javob berish.** Hozir `NonvoyTelegramBot.isTextOrContact` filtridan o'tmagan hamma narsa (rasm, stiker, ovoz) jimgina tashlanadi — foydalanuvchi stiker yuborsa bot javob bermaydi va nima kutilayotganini bilmaydi. Har state uchun "hozir nima kerakligi"ni eslatuvchi javob bo'lsin. Eslatma: chek rasmini qabul qilish (rejalashtirilgan feature) aynan shu filtrga tiqiladi
- [x] **README.md** — nima, nega, stack, ishga tushirish. Repo public va portfolio'ning bir qismi, README'siz repo tashrif buyuruvchi uchun bo'sh
- [x] **Flyway — deploy'dan OLDIN.** `ddl-auto: update` sxemani yarim yangilaydi: enum'ga yangi qiymat qo'shilsa Hibernate yaratgan `CHECK (state IN (...))` constraint'i eskiligicha qoladi va bot runtime'da `DataIntegrityViolationException` beradi (5-bosqichda `users_state_check` aynan shunday portladi); o'chirilgan maydon ustuni ham jadvalda qolib ketadi (`draft_quantity`, `available`). Lokal bazada qo'lda `ALTER` bilan tuzatish mumkin, prod'da esa yo'q. Reja: `ddl-auto: validate` + `V1__init.sql` (hozirgi sxema) va har o'zgarish uchun yangi migratsiya. Bajarildi: `V1__init.sql` entity'lardan Hibernate yordamida chiqarilgan, enum `CHECK`'lari ataylab yo'q (`validate` ularni tekshirmaydi, ya'ni migratsiya esdan chiqsa baribir runtime'da portlardi). Shu bilan birga: `DB_URL` env, `show-sql` prod konfiguratsiyadan olindi, `PAYMENT_CARD` / `PAYMENT_CARD_HOLDER` default'siz — env unutilsa dastur ishga tushmaydi (soxta karta mijozga ko'rinmasin)
- [ ] Deploy: arzon VPS yoki vaqtincha uy kompyuteri (long polling — statik IP shart emas). Eslatma: O'zbekistondan `api.telegram.org` to'siladi — lokal ishlab chiqishda VPN yoki proxy kerak, VPS chet elda bo'lsa muammo yo'q
- [ ] Novvoyga ko'rsatish, real test, tuzatishlar

### 5-bosqich: Savatcha va chek orqali to'lov (bajarildi)

Yangi flow — mijoz tomoni (buyurtma yaratilgungacha):

```
IDLE ──"Buyurtma berish"──► WAITING_PRODUCT
WAITING_PRODUCT ──mahsulot tanlandi──► WAITING_QUANTITY
WAITING_QUANTITY ──raqam──► savatga qo'shiladi ──► CART_REVIEW

CART_REVIEW   "Savat: Non×10 — 50 000 / Patir×5 — 30 000 / Jami: 80 000 so'm"
              [➕ Yana] [✅ To'g'ri] [❌ Bekor]
   ├ Yana    → WAITING_PRODUCT
   ├ To'g'ri → WAITING_RECEIPT ("<summa>ni <karta>ga o'tkazib, chek rasmini yuboring")
   └ Bekor   → savat tozalanadi → IDLE

WAITING_RECEIPT ──rasm──► file_id saqlanadi ──► FINAL_CONFIRM

FINAL_CONFIRM  chek rasmi + savat + jami summa
               [✅ Tasdiqlash] [❌ Bekor]
   ├ Tasdiqlash → Order + OrderItem yaratiladi → kassaga rasm+karta → IDLE
   ├ yangi rasm → chek almashtiriladi, ekran qayta chiziladi
   └ Bekor      → savat va chek tozalanadi → IDLE
```

Buyurtma yaratilgandan keyin — ikki guruh tomoni:

```
Order NEW ──► kassa guruhi: SendPhoto(chek) + caption + [✅ To'lov tasdiqlandi] [❌ Bekor]
              mijozga: "Buyurtmangiz #47 qabul qilindi. To'lov tekshirilmoqda ⏳"

kassa ✅ ──► NEW→ACCEPTED
              kassa kartasi: EditMessageCaption, tugma [❌ Bekor] qoladi
              ishchilar guruhi: SendMessage(karta) + [🍞 Tayyor]
              mijozga: "To'lovingiz tasdiqlandi ✅ Buyurtma tayyorlanmoqda"

ishchilar 🍞 ──► ACCEPTED→READY
              ishchilar kartasi: EditMessageText, tugmasiz
              mijozga: "Noningiz tayyor, olib ketishingiz mumkin 🍞 (#47)"

kassa ❌ (NEW da) ──► NEW→CANCELLED
              kassa kartasi: EditMessageCaption, tugmasiz
              mijozga: "To'lov topilmadi ❌ Iltimos, qaytadan buyurtma bering"

kassa ❌ (ACCEPTED da) ──► ACCEPTED→CANCELLED
              kassa kartasi: EditMessageCaption, tugmasiz
              ishchilar guruhiga YANGI xabar: "❌ Buyurtma #47 bekor qilindi — yopmang"
              mijozga: "Buyurtmangiz #47 bekor qilindi ❌"
```

Qarorlar va sabablari:

1. **Savatcha alohida `CartItem` entity'da**, `Order`da emas. Sabab: buyurtma faqat oxirgi tasdiqdan keyin yaratiladi, shunda bazada chala `Order` hech qachon qolmaydi va yangi status kerak bo'lmaydi.
2. **Chek yuborilmasa muammo yo'q** — buyurtma umuman yaratilmagan bo'ladi, faqat savat qoladi. Timeout yoki tozalovchi job kerak emas.
3. **Bir xil mahsulot ikkinchi marta qo'shilsa miqdor qo'shiladi** (yangi qator emas). Baza darajasida `(user_id, product_id)` unique constraint bilan kafolatlanadi.
4. **`CartItem`da narx maydoni yo'q** — savatda mahsulotning joriy narxi ko'rsatiladi, narx muzlatish esa `OrderItem.priceAtOrder`da. Natijada savat ochiq turganda narx o'zgarsa, mijoz ekranidagi son yangilanadi, bazaga nomuvofiqlik kirmaydi. **Aniqlashtirildi (34-qaror):** bu faqat summa aytilguncha — `✅ To'g'ri`dan keyin narx muzlatiladi.
5. **Savatni tahrirlash yo'q** (faqat "Yana" / "To'g'ri" / "Bekor"). Xato bo'lsa — bekor qilib boshidan. Sabab: MVP sodda qolsin; pozitsiyani o'chirish tugmasi keyin qo'shilishi mumkin.
6. **Chek — faqat rasm** (`photo`). Document/PDF qabul qilinmaydi.
7. **Albom (bir nechta rasm) rad etiladi**: `message.mediaGroupId != null` bo'lsa "faqat bitta rasm yuboring". Albom bir necha update bo'lib kelgani uchun oxirgi rad etilgan `mediaGroupId` xotirada eslab qolinadi — bot bir marta javob beradi. (Bazaga yozilmaydi: vaqtinchalik UI holati.)
8. **`photo` massividan eng kattasi olinadi** — bu turli o'lchamdagi bitta rasm, albom bilan chalkashtirilmasin.
9. **`FINAL_CONFIRM`da yangi rasm eskisini almashtiradi** — mijoz aynan shu qadamda chekini ko'radi va xato yuborganini payqaydi; rad etilsa butun buyurtmani qaytadan boshlashga majbur bo'lardi.
10. **Ikki guruh uchun yangi status KERAK EMAS.** `NEW` = "to'lov tekshirilmoqda", `ACCEPTED` = "to'lov tasdiqlandi, tandirga". Har guruhda bitta oldinga tugma: kassada `✅` (NEW→ACCEPTED), ishchilarda `🍞` (ACCEPTED→READY). O'tish validatsiyasi va eski buyurtmalar o'zgarmaydi.
11. **`REJECTED` alohida status yo'q** — chek rad etilishi ham `CANCELLED`. Mijozga boradigan matn farqi **eski statusdan** chiqariladi (`changeStatus` tranzaksiya ichida eski holatni biladi): `NEW→CANCELLED` "to'lov topilmadi", `ACCEPTED→CANCELLED` "bekor qilindi". Enum shishirilmaydi.
12. **Bekor qilish faqat kassada.** Pul kassada olingan — pul bilan bog'liq har qanday qaror bitta joyda tursin. Ishchilarda faqat `🍞 Tayyor`. Kassa `ACCEPTED`ni bekor qilsa ishchilarga alohida "yopmang" xabari ketadi, aks holda non allaqachon tandirda bo'ladi.
13. **Chek rasmi ishchilarga yuborilmaydi** — ularga nima yopish kerakligi kerak, pul emas. Yon foydasi: rasm faqat kassa xabarida, shuning uchun `EditMessageCaption` faqat o'sha yerda; ishchilar kartasi matn bo'lgani uchun hozirgi `EditMessageText` kodi tegilmaydi.
14. **Caption 1024 limiti uchun maxsus himoya yo'q.** 3-qarorga ko'ra savatda bir mahsulot bir marta uchraydi, ya'ni qatorlar soni mahsulot turlari soni bilan chegaralangan (3–5 ta). Limitga yetish uchun ~20 xil non kerak. O'rniga `OrderCardFormatter` caption uzunligi test bilan qoplanadi.
15. **`Order`da `messageId` saqlanmaydi.** Har karta faqat **o'z tugmasi** bosilganda qayta chiziladi. Oqibati: ishchilar `Tayyor` bosganda kassa kartasida `[❌ Bekor]` eskirgan holda qoladi (va teskarisi — kassa bekor qilganda ishchilarda `[🍞 Tayyor]`). Bu ataylab qabul qilingan: eskirgan tugma bosilsa `READY→CANCELLED` / `CANCELLED→READY` validatsiyadan o'tmaydi va hozirgi `IllegalStateException` shoxi javob berib klaviaturani o'zi tuzatadi. Alternativa — service `execute` qilib `messageId` o'qishi — service'lar `BotApiMethod` qaytaradigan arxitekturani buzadi, bitta ortiqcha tugma buni oqlamaydi. **Qisman qayta ko'rildi (35-qaror):** ishchilar `Tayyor` bosganda kassa kartasi endi yopiladi — id bazada emas, tugmaning o'zida.
16. **Rad etilgan buyurtmaga qayta chek yuborib bo'lmaydi** — `CANCELLED` terminal, mijoz boshidan boshlaydi (savat allaqachon tozalangan). Rad etish "pul kelmadi" degani, ya'ni baribir qaytadan to'lash kerak; `NEW→NEW` qayta-chek sub-flow'i state mashinasiga butun bir shox qo'shardi.
17. **Callback guruhga bog'lanadi**: `ACCEPT`/`CANCEL` faqat kassa guruhidan, `READY` faqat ishchilar guruhidan qabul qilinadi. Tugma u yerda chizilmasa ham himoya arzon.

Savatni tozalash kerak bo'lgan joylar (hech biri esdan chiqmasin):
`/start` · `CART_REVIEW` Bekor · `FINAL_CONFIRM` Bekor · buyurtma yaratilgandan keyin · `IDLE`dan yangi buyurtma boshlanganda (eski savat qolib ketmasin)

Vazifalar:
- [x] `CartItem` entity + repository, `UserState`ga yangi qadamlar, `User`ga `draftProductId` / `draftReceiptFileId` (`draftQuantity` o'rniga)
- [x] `CartService` — qo'shish (miqdor birlashtirish bilan), ro'yxat, jami summa, tozalash
- [x] Mahsulot tanlash qadami (`WAITING_PRODUCT`) va `ProductService.getActiveProduct()` o'rniga id bo'yicha qidirish
- [x] `CART_REVIEW` ekrani va "Yana" halqasi
- [x] Chek qadami (`WAITING_RECEIPT`) — rasm validatsiyasi, albom rad etish
- [x] `FINAL_CONFIRM` — chek rasmi + savat, buyurtma yaratish (`OrderService` savatdan `OrderItem` yasaydi, `priceAtOrder` muzlatadi), kassaga yuborish
- [x] `application.yml`: `bot.payment-card`, `bot.payment-card-holder` (default'siz — 4-bosqichda olib tashlandi), `bot.worker-group-id`

Ikki guruh vazifalari:
- [x] `Order`ga `receiptFileId`
- [x] `NonvoyTelegramBot` ikkala guruh id'sini `AdminFlowService`ga yo'naltiradi
- [x] `OrderCardFormatter.keyboard(order, audience)` — `audience` = `PAYMENT` | `KITCHEN`; kassa/ishchilar uchun alohida matn (ishchilarda telefon va narx yo'q)
- [x] Callback guruhga bog'lanadi (17-qaror)
- [x] Buyurtma yaratilganda kassaga `SendPhoto` + caption + tugmalar, mijozga "to'lov tekshirilmoqda"
- [x] Kassa `✅`: `EditMessageCaption` + ishchilarga karta + mijozga xabar
- [x] Kassa `❌` (`ACCEPTED` da): qo'shimcha ishchilarga "yopmang" xabari
- [x] Ishchilar `🍞`: `EditMessageText` + mijozga xabar
- [x] `CANCELLED` matni eski statusdan (11-qaror)
- [x] Testlar: ikki audience klaviaturasi, bekor qilish matnlari, caption uzunligi

Ochiq savollar:
- To'lov kartasi raqami va egasi novvoydan olinadi (default yo'q, majburiy `PAYMENT_CARD` / `PAYMENT_CARD_HOLDER` env)

### 6-bosqich: Mahsulot boshqaruvi (bajarildi)

Bosqichgacha `/mahsulotlar` faqat "bor/tugadi" toggle qilardi — mahsulot qo'shish, narx yoki nomni o'zgartirish faqat SQL orqali edi, ya'ni novvoy dasturchiga bog'lanib qolardi.

**Kirish nuqtasi:** `/mahsulotlar` (faqat kassa guruhida). `setMyCommands` orqali Telegram menyusida bosiladigan qator bo'lib turadi — admin qo'lda yozmaydi.

**1-ekran — ro'yxat.** Har mahsulot bitta inline tugma, narx tugma matnida (matnli ro'yxat takrorlanmaydi — bir ma'lumot ikki joyda turmasin):

```
🍞 Mahsulotlar

[Non — 5 000 so'm]
[Patir — 7 000 so'm]
[➕ Yangi mahsulot]
```

**2-ekran — mahsulot kartasi.** Yangi xabar emas, o'sha xabar `EditMessageText` bilan almashadi:

```
🍞 Non — 5 000 so'm

[💵 Narxni o'zgartirish]
[✏️ Nomini o'zgartirish]
[🗑 O'chirish]
[⬅️ Orqaga]
```

**3-qadam — matn so'rash.** `💵` / `✏️` / `➕` bosilganda bot alohida savol xabarini yuboradi (`ForceReply`, selective, adminni mention qilib), oxirida mashina o'qiydigan quyruq bilan:

```
Non uchun yangi narxni yozing (masalan: 6000)
[narx #3 @812]
```

Admin reply yozgach bot: qiymatni saqlaydi → qisqa tasdiq yuboradi → `@812` (ro'yxat xabarining messageId'si) yordamida eski ro'yxatni joyida qayta chizadi.

Qarorlar va sabablari:

18. **Matn kiritish faqat reply orqali, DB'da admin state YO'Q.** Sabab texnik: BotFather'da privacy mode default yoqilgan va bot guruhda faqat buyruqlarni, o'z xabariga qilingan reply'larni va mention'larni ko'radi — "keyingi matnni ushlayman" degan state mashinasi u xabarni umuman olmaydi. Privacy'ni o'chirish esa botga butun guruh suhbatini ochib beradi. Yon foydasi: `User.state` mijoz flow'ida tegilmay qoladi va ikki admin parallel ishlay oladi.
19. **Kontekst quyruqda: `[amal #productId @listMessageId]`.** 15-qarordagi kabi holat xabarning o'zida yashaydi, bazada emas. `@listMessageId` tufayli guruhda bitta "tirik" ro'yxat qoladi, yangi-yangi ro'yxatlar to'planmaydi.
20. **Xato xabari ham o'sha quyruq bilan tugaydi** — admin xato xabariga reply qilib qayta urinadi, halqa uzilmaydi va boshidan boshlash kerak emas.
21. **Qayta chizish muvaffaqiyatsiz bo'lsa bot buni bilmaydi** (service `BotApiMethod` qaytaradi, `execute` natijasini ko'rmaydi — 15-qaror bilan bir xil sabab). Aynan shuning uchun qisqa tasdiq xabari majburiy: ro'yxat yangilanmasa ham admin amal bajarilganini ko'radi.
22. **`available` maydoni butunlay olib tashlanadi.** Non tugasa odam navbatga yoziladi, ketmaydi — ya'ni "bugun yo'q" holati hech qachon foydali ish qilmaydi, lekin har ekranda shart-tekshiruv qo'shadi. Prod baza Flyway `V1`dan toza yaratiladi, ustun u yerda umuman yo'q.
23. **Arxivlash yo'q — o'chirish haqiqiy `delete`.** Lekin `OrderItem` → `Product` FK eski buyurtma tarixini ushlab turadi, shuning uchun `OrderItem`ga `productNameAtOrder` qo'shiladi va `product` FK `nullable` bo'ladi: nom ham xuddi `priceAtOrder` kabi muzlatiladi, mahsulot o'chsa eski karta baribir to'liq chiqadi. O'chirishda service avval shu mahsulotga ishora qilayotgan `OrderItem`larni `null`ga qo'yadi va `CartItem`larni o'chiradi (34-qarordan keyin: faqat muzlatilmaganlarini, muzlatilganlari ham `null`ga qo'yiladi).
24. **O'chirishda tasdiq tugmasi bor** (`➕` bilan qaytarish nomni va narxni qayta yozishni talab qiladi, ya'ni bir bosishda qaytmaydi). Boshqa joylarda tasdiq so'ramaymiz, bu yerda so'raymiz — chunki amal haqiqatan ham yo'qotuvchi.
25. **`➕` bitta qadamda:** `Nom va narxni yozing: Non 6000` — oxirgi token narx, qolgani nom (bo'sh joyli nom ishlaydi). Yiliga bir-ikki marta ishlatiladigan amal uchun ikki qadamli sehrgar ortiqcha.
26. **Validatsiya:** narx — butun son, ajratgichlar (`6 000`, `6_000`) tozalanadi, `0` va manfiy rad; nom — bo'sh emas, ≤ 32 belgi (inline tugmaga nom + narx sig'ishi kerak), `/` bilan boshlanmaydi, unique.
27. **Faqat kassa guruhida.** Narx — pul masalasi (12-qaror).
28. **Service'lar `BotApiMethod` emas, `PartialBotApiMethod` qaytaradi.** `SendPhoto` `BotApiMethod` emas (rasm multipart bilan ketadi), ya'ni chek rasmi bilan ishlash uchun tur kengaytirilishi shart edi. Tur ajratish `NonvoyTelegramBot.send`da, bitta joyda. 15-qarorning mohiyati o'zgarmaydi: service baribir `execute` natijasini ko'rmaydi.
29. **Mahsulot boshqaruvi `ProductAdminService`da, `AdminFlowServiceImpl`da emas** (rejada shunday yozilgan edi). Ikkalasi bitta klassda bo'lsa fayl 400 qatordan oshib, ikki butunlay boshqa mavzu (buyurtma statusi va mahsulot CRUD'i) aralashib ketardi. `AdminFlowServiceImpl` faqat yo'naltiradi.

Vazifalar:
- [x] `Product`dan `available` olib tashlanadi; `ProductRepository.findFirstByAvailableTrueOrderByIdAsc`, `ProductService.getActiveProduct`/`toggleAvailability` va `AdminFlowServiceImpl`dagi toggle shoxi o'chadi
- [x] `OrderItem`ga `productNameAtOrder`, `product` FK `nullable`
- [x] `ProductService`: `create`, `updatePrice`, `rename`, `delete`
- [x] `ProductPrompt` util — `[amal #id @msgId]` quyrug'ini yasash va o'qish (sof funksiya, test qilish oson)
- [x] `ProductInput` — `Non 6000` va narx matni + validatsiya xabarlari
- [x] `ProductAdminService`: 1/2-ekran, `P:*` callback'lar, reply handler
- [x] `setMyCommands` (kassa guruhi scope)
- [x] Testlar: quyruq parse, nom+narx parse, o'chirish eski buyurtma kartasini buzmasligi, validatsiya chegaralari

### 7-bosqich: Deploy oldidan tuzatishlar

Kod review'da real ishlatishda chiqadigan uchta muammo topildi. Branch: `feature/pre-deploy-fixes`.

1. **Kassaga karta yetmasa mijoz abadiy qulflanadi.** Buyurtma `NEW` bo'lib yoziladi, kassaga `SendPhoto` esa tarmoq xatosida faqat logga tushardi. Kassa buyurtmani ko'rmaydi, mijoz esa "bitta tekshirilmagan buyurtma" qulfi tufayli yangisini ham bera olmaydi. `NEW→ACCEPTED`da ishchilar kartasi yo'qolsa ham xuddi shunday.
2. **Chek yuborilgach narx o'zgarsa yoki mahsulot o'chirilsa**, buyurtma summasi mijoz o'tkazgan summadan farq qiladi, savat bo'shab qolsa buyurtma umuman yaratilmaydi.
3. **Miqdor `int` to'lib ketadi** — bir mahsulot ikki marta ~2 mlrd qo'shilsa miqdor va summa manfiy.

Qarorlar va sabablari:

30. **Qayta urinish bot qatlamida, faqat tarmoq xatosida** (1 s, 3 s) va 30 s gacha bo'lgan `429`da. Qaror `SendRetryPolicy`da — sof funksiya. Boshqa API xatolari (`chat not found`, `message is not modified`) qayta urinish bilan tuzalmaydi. Update'lar bitta oqimda ishlangani uchun kutish hammani kutdiradi — shuning uchun urinishlar soni va `retry_after` chegaralangan. Timeout'da so'rov aslida yetgan bo'lishi mumkin, ya'ni takroriy karta chiqadi — bu xavfsiz (31-qaror).
31. **`/buyurtmalar` — ochiq kartalarni qayta chiqarish**, har guruh o'zinikini: kassada `NEW` + `ACCEPTED` (chek bilan), ishchilarda `ACCEPTED`. Service yuborish natijasini ko'rmaydi (15-qaror), shuning uchun "yetmadi"ni avtomatik aniqlash o'rniga guruh o'zi so'raydi; mijozning qulf xabarida "uzoq cho'zilsa, novvoyxonaga ayting" qatori bor. Takroriy karta xavfsiz: ikkinchi bosish validatsiyadan o'tmaydi va karta o'zini tuzatadi. Kartalar faqat so'ralganda chiqadi — ishchilarga kutilmagan takroriy karta tushib, non ikki marta yopilmaydi. Rad etilgan variantlar: qulfni vaqt bilan ochish (yo'qolgan buyurtmani yashiradi, mijoz ikkinchi marta to'laydi) va outbox + scheduler (`messageId` saqlash kerak — 15, 28-qarorlarni buzadi).
32. **`/buyurtmalar` limiti 20 ta** — Telegram guruhga daqiqasiga ~20 xabar ruxsat beradi. Kassada `NEW` oldin: ko'p `ACCEPTED` yangi to'lovlarni limitdan siqib chiqarmasin. Qolganlar uchun "yana N ta bor" qatori.
33. **Karta yasash `OrderCardService`da.** Kassa kartasi endi ikki joyda yasaladi (buyurtma yaratilganda va `/buyurtmalar`da), ishchilar kartasi ham — takrorlanmasin.
34. **Savat summa aytilgan paytda muzlatiladi** (`CART_OK`): `CartItem.nameAtCheckout` / `priceAtCheckout`. Shu paytdan narx va'da qilingan — mijoz aynan shu summani o'tkazadi. 4-qaror bekor bo'lmaydi, aniqlashadi: "savatda joriy narx — summa aytilguncha". Mahsulot o'chirilsa muzlatilgan qator o'chmaydi, faqat `product` `null` bo'ladi (`OrderItem` bilan bir xil yo'l, 23-qaror), muzlatilmaganlari esa avvalgidek o'chadi. `WAITING_RECEIPT`dan savatga qaytish yo'li yo'q (faqat `/start`, u tozalaydi) — muzlagan qator hech qachon "erimaydi". Rad etilgan variant: kassaga "mijozga X aytilgan edi" ogohlantirishi — faqat narx o'zgarishini yopadi (o'chirilgan mahsulot baribir tushib qoladi) va qarorni odamga yuklaydi. V2 migratsiya.

Vazifalar:
- [x] `SendRetryPolicy` + `NonvoyTelegramBot.sendWithRetry`
- [x] `OrderCardService`: `paymentCard`, `kitchenCard`, `openCards`
- [x] `/buyurtmalar` ikkala guruhda, `setMyCommands` ishchilar guruhiga ham
- [x] Mijozning qulf xabariga qo'shimcha qator
- [x] Narxni summa aytilganda muzlatish (2-muammo): V2 migratsiya, `CartService.freeze`, `CartItem.displayName`/`unitPrice`
- [x] Miqdor overflow (3-muammo): `Math.addExact`, "Yuqori chegara yo'q" qarori o'zgarmaydi — faqat `int`ning o'z chegarasi hurmat qilinadi, mijoz miqdor qadamida qoladi

Real testda chiqqan (branch `fix/stale-cancel-button`): ishchilar `Tayyor` bosgach kassa kartasida `[❌ Bekor]` qolardi, bosilsa `READY->CANCELLED` rad etilardi. Xatti-harakat to'g'ri edi (15-qaror), lekin log stack trace bilan yozilgani uchun yiqilishga o'xshardi va kassa ma'nosiz tugmani ko'rib turardi.

35. **Kassa kartasining `messageId`si ishchilar tugmasida: `READY:47:812`.** Kassa `✅` bosganda callback aynan kassa kartasidan keladi, ya'ni uning id'si shu paytda ma'lum — u ishchilar kartasiga uzatiladi. `Tayyor` bosilganda kassa kartasi `EditMessageCaption` bilan "tayyor" holatida, tugmasiz qayta chiziladi. 19-qarordagi `@listMessageId` bilan bir xil g'oya: holat xabarning o'zida, bazada ustun yo'q, service'lar baribir yuborish natijasini ko'rmaydi (15, 28-qarorlar saqlanadi). Uchinchi qism ixtiyoriy — `/buyurtmalar` bilan qayta chiqarilgan ishchilar kartasida va deploy'dan oldingi tugmalarda u yo'q, u holda kassa kartasi yangilanmaydi va eskirgan tugmani validatsiya ushlaydi. Teskari yo'nalish (kassa `ACCEPTED`ni bekor qilganda ishchilar kartasini yopish) qilinmaydi: ishchilar kartasining id'si kassaga kelmaydi, o'rniga "yopmang" xabari bor (12-qaror). Eskirgan tugma logi endi bitta qator, stack trace'siz — bu kutilgan holat.

- [x] Eskirgan tugma logi stack trace'siz
- [x] `OrderAction` uch qismli callback (ikki qismlisi ham o'qiladi), `kitchenCard(order, paymentMessageId)`, `READY`da kassa kartasini qayta chizish

36. **Vaqt mintaqasi kodda: `bot.time-zone` (`TIME_ZONE`, default `Asia/Tashkent`).** `createdAt`/`updatedAt` `LocalDateTime.now()` bilan to'ldiriladi, u JVM mintaqasini oladi — chet el VPS'i odatda UTC'da, vaqt 5 soat orqada yozilardi. Hozir vaqt hech qayerda ko'rsatilmaydi, lekin statistika qo'shilganda bazada aralash vaqtlar yig'ilib qolgan bo'lardi. `TimeZoneInitializer` kontekst yaratilishidan oldin (`ApplicationEnvironmentPreparedEvent`) va logging tinglovchisidan ham oldin ishlaydi — shunda bazada ham, loglarda ham vaqt bir xil. Nom `ZoneId.of` bilan tekshiriladi: `TimeZone.getTimeZone` xato nomda jimgina GMT qaytarardi. To'lov kartasidan farqli (4-bosqich, Flyway bandi) default bor, chunki bu yerda to'g'ri default mavjud va xato qiymat pulga tegmaydi. Rad etilgan: `Instant` + `timestamptz` (bitta mamlakatdagi bitta novvoyxona uchun 5 jadvallik migratsiya ortiqcha) va faqat serverda `TZ` qo'yish (esdan chiqadi, serverga bog'lanib qoladi).

- [x] `TimeZoneInitializer` + `bot.time-zone`

### Parallel vazifa (kod emas)
- [ ] Novvoy bilan gaplashish: non narxi, turlari, buyurtmalarni kim ko'radi, Telegram guruhga rozimi
- [ ] To'lov kartasi raqami va egasining ismi (bot chek so'raganda ko'rsatadi)

## Git / GitHub tartibi

Bu loyiha GitHub'da public repo bo'ladi — portfolio'ning bir qismi. Qoidalar:

1. **Commit birligi — mantiqiy tugallangan ish**, kun oxiri emas. "Entity'lar yozildi" — bitta commit, "status validatsiya qo'shildi" — alohida commit. Bitta ulkan "kunlik ish" commit'i taqiqlanadi.
2. **Kuniga kamida bitta push** — ishlangan kunda contribution graph bo'sh qolmasin.
3. **Commit message formati** (soddalashtirilgan conventional commits, inglizcha):
    - `feat: add order entities and status enum`
    - `fix: prevent double callback on order buttons`
    - `refactor: extract order card formatting`
    - `chore: setup project skeleton`
    - `docs: update readme`
    - Message nima qilinganini aytadi, "update", "changes", "ish" kabi bo'sh so'zlar taqiqlanadi.
4. **Branch strategiyasi:** solo loyiha uchun sodda — `main` har doim ishlaydigan holatda, har bosqich (yoki katta feature) `feature/...` branch'da yoziladi va tugagach merge qilinadi. Masalan: `feature/entities`, `feature/customer-flow`, `feature/admin-flow`.
5. **Birinchi commit'dan oldin:** `.gitignore` (target/, .idea/, .env, *.iml), token va parollar HECH QACHON commit qilinmaydi.
6. **README.md** loyiha oxirida emas, boshida yaratiladi va bosqichma-bosqich to'ldiriladi (nima, nega, stack, ishga tushirish).
7. **Claude'ning roli:** har vazifa yakunida Claude "hozir commit payti, message taxminan bunday" deb eslatib turadi va commit message'larni review qiladi. Vazifa berilganda qaysi branch'da ishlash ham aytiladi.

## Keyinroqqa qoldirilgan (MVP'ga KIRMAYDI)

- Onlayn to'lov (Click/Payme)
- Yetkazib berish / kuryer
- Oldindan (aniq vaqtga) buyurtma / vaqt tanlash
- Statistika / web dashboard
- Multi-tenant
- Mahsulot rasmlari
