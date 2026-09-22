package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;

/**
 * Status o'zgarishi natijasi. Eski status ham qaytariladi, chunki mijozga boradigan
 * matn aynan undan chiqadi: NEW→CANCELLED "to'lov topilmadi", ACCEPTED→CANCELLED
 * "bekor qilindi" (11-qaror). Eski holatni tranzaksiyadan tashqarida o'qish ishonchsiz —
 * ikki admin bir vaqtda tugma bossa qaysi biri birinchi bo'lgani bilinmaydi.
 */
public record StatusChange(Order order, OrderStatus from) {
}
