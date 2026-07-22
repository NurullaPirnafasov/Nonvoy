# Nonvoy Bot — Mahalla novvoyxonasi uchun buyurtma boti

## Loyiha haqida

Mahallada faoliyat yuritadigan bitta novvoyxona uchun Telegram bot. Mijozlar botdan non buyurtma qiladi (asosan ko'p miqdorda, muzlatkichga), tayyor bo'lganda xabar oladi va **o'zlari olib ketadi** (pickup only). Yetkazib berish, onlayn to'lov, multi-tenant — YO'Q.

**Asosiy printsip:** flow maksimal sodda, ichki struktura toza (qatlamlar, enum'lar, narx muzlatish) — shunda keyin feature qo'shish og'riqsiz bo'ladi.

## Ishlash tartibi (MUHIM — Claude uchun qoidalar)

Bu loyihada Nurulla kod yozadi, Claude **senior reviewer / team lead** rolida:

1. Claude vazifani aniq beradi: nima qilish kerak, qanday natija kutiladi, qaysi tuzoqlarga e'tibor berish kerak.
2. Nurulla kodni **o'zi yozadi**, keyin review'ga tashlaydi.
3. Claude review qiladi: xatolarni ko'rsatadi, lekin **tayyor kod yozib bermaydi** — yo'naltiruvchi savollar va hint'lar beradi.
4. Nurulla tiqilib qolsa (30+ daqiqa), Claude pseudocode yoki kichik misol berishi mumkin, lekin to'liq yechimni emas.
5. Istisno: boilerplate/config fayllar (pom.xml, application.yml, docker-compose) — bularni Claude yozib berishi mumkin.
6. Dizayn qarorlari asoslanadi: "menimcha" yetarli emas, har bir tanlov uchun sabab aytiladi.

## Texnik stack

- Java 21, Spring Boot 3.3.x, Maven
- PostgreSQL
- telegrambots kutubxonasi, long polling (webhook keyinroq)
- Package: `uz.nonvoy.bot` (yoki mijoz nomiga mos)

## Arxitektura

Oddiy layered: `Bot handler → Service → Repository`. Mapper/Event/Integration layer'lar KERAK EMAS.

### Entity'lar (4 ta, hammasi Auditable'dan meros: createdAt, updatedAt)

- **User** — telegramId, phone (contact orqali), name, role (CUSTOMER/ADMIN), state (bot flow qadami)
- **Product** — nomi, narxi, available (boolean "bugun bor/yo'q"). Hozircha bitta qator ("Non"), lekin yangi turlar (patir, shirmoy) qo'shilishi kutiladi
- **Order** — user, status, jami summa. Buyurtma raqami sifatida `Auditable.id` ishlatiladi (alohida `orderNumber` maydoni YO'Q — id unique, hisoblagich kerak emas; sequence'da uzilish/sakrash bo'lishi mumkin, lekin novvoyxona uchun raqam shunchaki identifikator, ketma-ketlik ma'no bermaydi)
- **OrderItem** — order, product, quantity, **priceAtOrder** (buyurtma paytidagi narx muzlatiladi — mahsulot narxi keyin o'zgarsa buyurtma tarixi buzilmasligi uchun)

### Status flow (sodda)

```
NEW → ACCEPTED → READY
  ↘ CANCELLED (NEW yoki ACCEPTED holatidan)
```

COMPLETED yo'q — olib ketishni kuzatish novvoyga ortiqcha yumush. Status o'tishlari service'da validatsiya qilinadi, noto'g'ri o'tish exception beradi.

### Bot state management

Foydalanuvchi flow'da qaysi qadamda ekani DB'da saqlanadi (`User.state` enum). Redis KERAK EMAS.

## Mijoz flow

1. `/start` → agar telefon yo'q bo'lsa, contact button orqali so'raladi (bir marta)
2. "Nechta non kerak?" → foydalanuvchi raqamni yozib kiritadi (validatsiya: 1–100)
3. Tasdiqlash → "Buyurtma #47 qabul qilindi ✅ Jami: 10 ta non — 50 000 so'm"
4. Status o'zgarganda avtomatik xabar (ayniqsa READY: "Noningiz tayyor, olib ketishingiz mumkin 🍞")
5. Tungi buyurtmalar ham qabul qilinadi — NEW bo'lib navbatda turadi, novvoy ertalab ko'radi. Ish vaqti validatsiyasi YO'Q.

Eslatma: hozir mahsulot bitta bo'lgani uchun tanlash qadami yo'q. Ikkinchi mahsulot qo'shilganda flow boshiga bitta tanlash qadami kiradi — arxitektura o'zgarmaydi.

## Novvoy (admin) flow

Alohida panel YO'Q. Buyurtmalar **Telegram guruhga** (kanal emas — tugma bosishni boshqarish guruhda qulay) karta ko'rinishida tushadi:

```
🆕 Buyurtma #47
👤 Alisher (+998 9x xxx xx xx)
🍞 Non × 10 — 50 000 so'm
[✅ Qabul] [🍞 Tayyor] [❌ Bekor]
```

Tugma bosilganda status o'zgaradi va mijozga xabar ketadi.

Admin buyruq: `/mahsulotlar` — ro'yxat + "bor/tugadi" toggle tugmasi.

## Reja (kuniga 3-4 soat, jami ~2-2.5 hafta)

### 1-bosqich: Skelet va domain (2 kun)
- [ ] Spring Boot loyiha setup (pom.xml, application.yml, PostgreSQL ulanish)
- [ ] BotFather'da bot yaratish, long polling, `/start`ga oddiy javob
- [ ] 4 ta entity + Auditable + enum'lar + repository'lar
- [ ] Status o'tish validatsiyasi (service metod)

### 2-bosqich: Mijoz flow (3-4 kun)
- [ ] State management (User.state asosida update routing)
- [ ] Ro'yxatdan o'tish (contact button)
- [ ] Miqdor so'rash (qo'lda raqam yozish, validatsiya: 1–100)
- [ ] Tasdiqlash + buyurtma yaratish + buyurtma raqami

### 3-bosqich: Admin flow (2-3 kun)
- [ ] Buyurtma kartasi admin guruhga yuborish
- [ ] Callback tugmalar → status o'zgartirish → mijozga notification
- [ ] `/mahsulotlar` boshqaruvi (toggle)

### 4-bosqich: Sayqallash va topshirish (2-3 kun)
- [ ] Exception handling, edge case'lar (ikki marta bosish, noto'g'ri raqam, bekor qilingan buyurtmani "Tayyor" qilish)
- [ ] Deploy: arzon VPS yoki vaqtincha uy kompyuteri (long polling — statik IP shart emas)
- [ ] Novvoyga ko'rsatish, real test, tuzatishlar

### Parallel vazifa (kod emas)
- [ ] Novvoy bilan gaplashish: non narxi, turlari, buyurtmalarni kim ko'radi, Telegram guruhga rozimi

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
- COMPLETED status / olib ketishni kuzatish
