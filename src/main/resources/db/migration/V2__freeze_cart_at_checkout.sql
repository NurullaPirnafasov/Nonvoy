-- Mijozga to'lanadigan summa aytilgan paytda (CART_OK) savat qatorlari muzlatiladi:
-- mijoz aynan shu summani o'tkazadi, keyin narx o'zgarsa yoki mahsulot o'chirilsa ham
-- buyurtma o'tkazilgan summa bilan yaratiladi (34-qaror).

alter table cart_items add column name_at_checkout varchar(255);
alter table cart_items add column price_at_checkout numeric(38, 2);

-- Muzlatilgan qatorda mahsulot o'chirilsa ishora uziladi, qator qoladi — order_item bilan
-- bir xil yo'l (23-qaror). Unique (user_id, product_id) buzilmaydi: PostgreSQL'da
-- null'lar bir-biriga teng emas
alter table cart_items alter column product_id drop not null;
