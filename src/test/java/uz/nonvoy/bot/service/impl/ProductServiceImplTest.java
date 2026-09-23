package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.repository.CartItemRepository;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void createStoresNameAndPrice() {
        when(productRepository.findByNameIgnoreCase("Patir")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Product created = productService.create("Patir", BigDecimal.valueOf(7000));

        assertEquals("Patir", created.getName());
        assertEquals(BigDecimal.valueOf(7000), created.getPrice());
    }

    /** Takroriy nom guruhda ikki bir xil tugma chiqaradi. */
    @Test
    void duplicateNameIsRejected() {
        when(productRepository.findByNameIgnoreCase("non")).thenReturn(Optional.of(product(1L, "Non")));

        assertThrows(IllegalStateException.class, () -> productService.create("non", BigDecimal.valueOf(5000)));
        verify(productRepository, never()).save(any());
    }

    /** O'z nomini o'ziga qayta berish xato emas (masalan katta-kichik harf tuzatish). */
    @Test
    void renameAllowsKeepingOwnName() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "non")));
        when(productRepository.findByNameIgnoreCase("Non")).thenReturn(Optional.of(product(1L, "non")));
        when(productRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals("Non", productService.rename(1L, "Non").orElseThrow().getName());
    }

    @Test
    void renameRejectsAnotherProductsName() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Non")));
        when(productRepository.findByNameIgnoreCase("Patir")).thenReturn(Optional.of(product(2L, "Patir")));

        assertThrows(IllegalStateException.class, () -> productService.rename(1L, "Patir"));
    }

    /**
     * O'chirishdan oldin FK'lar bo'shatiladi: eski buyurtmalar qoladi (ishora uziladi),
     * savatlardagi qatorlar esa o'chadi (23-qaror).
     */
    @Test
    void deleteDetachesOrderItemsBeforeRemovingProduct() {
        Product product = product(1L, "Non");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertTrue(productService.delete(1L));

        InOrder order = inOrder(orderItemRepository, cartItemRepository, productRepository);
        order.verify(orderItemRepository).detachProduct(1L);
        order.verify(cartItemRepository).deleteUnfrozenByProductId(1L);
        // Summasi aytilgan qator o'chmaydi — mijoz uni to'lagan bo'lishi mumkin (34-qaror)
        order.verify(cartItemRepository).detachProduct(1L);
        order.verify(productRepository).delete(product);
    }

    @Test
    void deletingMissingProductChangesNothing() {
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        assertFalse(productService.delete(9L));
        verify(orderItemRepository, never()).detachProduct(any());
        verify(productRepository, never()).delete(any());
    }

    private Product product(Long id, String name) {
        Product product = Product.builder().name(name).price(BigDecimal.valueOf(5000)).build();
        product.setId(id);
        return product;
    }
}
