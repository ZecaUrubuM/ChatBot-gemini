package com.chatbot.repository;

import com.chatbot.entity.Produto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Test
    void deveCarregarCatalogoInicial() {
        assertThat(produtoRepository.count()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void deveBuscarPorNomeIgnorandoCaixa() {
        List<Produto> encontrados = produtoRepository.findByNomeContainingIgnoreCase("arroz");

        assertThat(encontrados).isNotEmpty();
        assertThat(encontrados.getFirst().getNome()).containsIgnoringCase("Arroz");
        assertThat(encontrados.getFirst().getPreco()).isEqualByComparingTo("24.90");
        assertThat(encontrados.getFirst().isDisponivel()).isTrue();
    }

    @Test
    void deveBuscarPorTermoEmNomeDescricaoOuCategoria() {
        assertThat(produtoRepository.buscarPorTermo("uht")).isNotEmpty();
        assertThat(produtoRepository.findDistinctCategorias())
                .contains("Mercearia", "Laticínios", "Bebidas", "Limpeza", "Hortifruti", "Padaria", "Açougue");
    }

    @Test
    void deveIdentificarProdutoEsgotado() {
        List<Produto> aguaSanitaria = produtoRepository.findByNomeContainingIgnoreCase("Água Sanitária");

        assertThat(aguaSanitaria).hasSize(1);
        assertThat(aguaSanitaria.getFirst().isDisponivel()).isFalse();
    }
}
