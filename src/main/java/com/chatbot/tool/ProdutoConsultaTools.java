package com.chatbot.tool;

import com.chatbot.entity.Produto;
import com.chatbot.repository.ProdutoRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Ferramentas (function calling) que o Gemini usa para consultar o catálogo
 * SQL em tempo real. O starter do LangChain4j detecta métodos {@code @Tool}
 * em beans Spring e os registra automaticamente no {@code @AiService}.
 */
@Service
@Transactional(readOnly = true)
public class ProdutoConsultaTools {

    private static final Logger log = LoggerFactory.getLogger(ProdutoConsultaTools.class);
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private final ProdutoRepository produtoRepository;

    public ProdutoConsultaTools(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Tool("""
            Busca produtos no catálogo do supermercado pelo nome, descrição ou categoria.
            Use SEMPRE que o cliente perguntar sobre um produto, preço, descrição ou disponibilidade.
            Aceita parte do nome (ex: arroz, leite, coca, frango).
            """)
    public String buscarProdutosPorNome(
            @P("Nome, marca ou termo de busca do produto (ex: arroz, leite, refrigerante)") String termo
    ) {
        log.debug("Tool buscarProdutosPorNome termo={}", termo);
        if (termo == null || termo.isBlank()) {
            return "Informe um termo para buscar no catálogo.";
        }

        List<Produto> produtos = produtoRepository.buscarPorTermo(termo.trim());
        if (produtos.isEmpty()) {
            produtos = produtoRepository.findByNomeContainingIgnoreCase(termo.trim());
        }
        return formatarLista(produtos, "Nenhum produto encontrado para \"" + termo + "\".");
    }

    @Tool("""
            Lista os produtos de uma categoria do supermercado.
            Categorias existentes: Mercearia, Laticínios, Bebidas, Limpeza, Hortifruti, Padaria, Açougue.
            """)
    public String buscarProdutosPorCategoria(
            @P("Nome da categoria, ex: Mercearia, Laticínios, Bebidas, Limpeza, Hortifruti, Padaria, Açougue") String categoria
    ) {
        log.debug("Tool buscarProdutosPorCategoria categoria={}", categoria);
        if (categoria == null || categoria.isBlank()) {
            return "Informe uma categoria. Use listarCategorias para ver as opções.";
        }

        List<Produto> produtos = produtoRepository.findByCategoriaContainingIgnoreCase(categoria.trim());
        return formatarLista(produtos, "Nenhum produto encontrado na categoria \"" + categoria + "\".");
    }

    @Tool("Lista todas as categorias de produtos cadastradas no supermercado.")
    public String listarCategorias() {
        log.debug("Tool listarCategorias");
        List<String> categorias = produtoRepository.findDistinctCategorias();
        if (categorias.isEmpty()) {
            return "Nenhuma categoria cadastrada no momento.";
        }
        return "Categorias disponíveis: " + String.join(", ", categorias) + ".";
    }

    @Tool("Consulta um produto pelo identificador numérico (id) e retorna descrição, preço e estoque exatos.")
    public String consultarProdutoPorId(
            @P("Identificador numérico do produto") Long id
    ) {
        log.debug("Tool consultarProdutoPorId id={}", id);
        if (id == null) {
            return "Informe o id numérico do produto.";
        }
        return produtoRepository.findById(id)
                .map(this::formatarProduto)
                .orElse("Nenhum produto cadastrado com o id " + id + ".");
    }

    @Tool("Lista produtos com estoque disponível (quantidade maior que zero). Use quando o cliente quiser o que está em promoção de disponibilidade ou o que tem na loja agora.")
    public String listarProdutosEmEstoque() {
        log.debug("Tool listarProdutosEmEstoque");
        List<Produto> produtos = produtoRepository.findByEstoqueGreaterThan(0);
        return formatarLista(produtos, "Nenhum produto disponível em estoque no momento.");
    }

    private String formatarLista(List<Produto> produtos, String mensagemVazia) {
        if (produtos == null || produtos.isEmpty()) {
            return mensagemVazia + " Sugira ao cliente buscar por outro nome ou listar as categorias.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrados ").append(produtos.size()).append(" produto(s):\n");
        for (Produto produto : produtos) {
            sb.append(formatarProduto(produto)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatarProduto(Produto produto) {
        String disponibilidade = produto.isDisponivel()
                ? produto.getEstoque() + " unidade(s) em estoque"
                : "ESGOTADO (0 unidades)";

        return """
                - id: %d
                  nome: %s
                  categoria: %s
                  descricao: %s
                  preco: %s
                  estoque: %s
                """.formatted(
                produto.getId(),
                produto.getNome(),
                produto.getCategoria(),
                produto.getDescricao(),
                BRL.format(produto.getPreco()),
                disponibilidade
        ).trim();
    }
}
