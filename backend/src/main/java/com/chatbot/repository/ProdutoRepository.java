package com.chatbot.repository;

import com.chatbot.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acesso ao catálogo de produtos.
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByNomeContainingIgnoreCase(String nome);

    List<Produto> findByCategoriaContainingIgnoreCase(String categoria);

    List<Produto> findByEstoqueGreaterThan(Integer estoque);

    @Query("""
            SELECT p FROM Produto p
            WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%'))
               OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))
               OR LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))
            ORDER BY p.nome
            """)
    List<Produto> buscarPorTermo(@Param("termo") String termo);

    @Query("SELECT DISTINCT p.categoria FROM Produto p ORDER BY p.categoria")
    List<String> findDistinctCategorias();
}
