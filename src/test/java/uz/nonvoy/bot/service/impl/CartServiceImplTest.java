package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.repository.CartItemRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    /** Bir xil mahsulot ikkinchi marta qo'shilsa yangi qator emas, miqdor qo'shiladi (3-qaror). */
    @Test
    void addingSameProductMergesQuantity() {
        User user = user();
        Product non = product("Non", 5000);
        when(cartItemRepository.findByUserIdAndProductId(1L, 7L))
                .thenReturn(Optional.of(CartItem.builder().user(user).product(non).quantity(10).build()));
        when(cartItemRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals(15, cartService.add(user, non, 5).getQuantity());
    }

    @Test
    void addingNewProductStartsFromZero() {
        User user = user();
        Product non = product("Non", 5000);
        when(cartItemRepository.findByUserIdAndProductId(1L, 7L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        CartItem added = cartService.add(user, non, 3);

        assertEquals(3, added.getQuantity());
        assertEquals(non, added.getProduct());
    }

    /** Savatda narx muzlatilmaydi — jami har safar joriy narxlardan hisoblanadi (4-qaror). */
    @Test
    void totalUsesCurrentPrices() {
        Product non = product("Non", 5000);
        List<CartItem> items = List.of(
                CartItem.builder().product(non).quantity(10).build(),
                CartItem.builder().product(product("Patir", 7000)).quantity(5).build());

        assertEquals(BigDecimal.valueOf(85000), cartService.calculateTotal(items));

        non.setPrice(BigDecimal.valueOf(6000));
        assertEquals(BigDecimal.valueOf(95000), cartService.calculateTotal(items));
    }

    @Test
    void emptyCartTotalIsZero() {
        assertEquals(BigDecimal.ZERO, cartService.calculateTotal(List.of()));
    }

    @Test
    void clearRemovesEveryItemOfUser() {
        cartService.clear(user());

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(cartItemRepository).deleteByUserId(captor.capture());
        assertEquals(1L, captor.getValue());
    }

    private User user() {
        User user = User.builder().telegramId(123L).name("Alisher").build();
        user.setId(1L);
        return user;
    }

    private Product product(String name, long price) {
        Product product = Product.builder().name(name).price(BigDecimal.valueOf(price)).build();
        product.setId(7L);
        return product;
    }
}
