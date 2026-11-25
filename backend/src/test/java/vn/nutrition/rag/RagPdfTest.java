package vn.nutrition.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagPdfTest {
    private static final Path DOCS = Path.of("..", "docs");

    @Test void US05_TC10_FDA_uses_physical_pages_and_automatic_text() {
        var file = new FileSystemResource(DOCS.resolve("fixtures/fda-food-allergies-2024.pdf"));
        var chunks = RagPdf.read(file, RagPdf.ranges("1-2"), Map.of());
        assertThat(chunks).extracting(RagPdf.Chunk::pageStart).containsOnly(1, 2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.pageEnd()).isEqualTo(chunk.pageStart());
            assertThat(chunk.extractionMethod()).isEqualTo("PDF_TEXT");
        });
        var text = chunks.stream().collect(Collectors.groupingBy(RagPdf.Chunk::pageStart,
            Collectors.mapping(RagPdf.Chunk::content, Collectors.joining("\n"))));
        assertThat(text.get(1)).contains("Soybeans", "Sesame", "Peanuts");
        assertThat(text.get(2)).contains("In parentheses", "Contains");
    }

    @Test void US05_TC10_reviewed_Vietnam_table_rows_keep_physical_page_and_exact_units() {
        var file = new FileSystemResource(DOCS.resolve("corpus/bangthanhphanthucphamVN.pdf"));
        var chunks = RagPdf.read(file, RagPdf.ranges("3,14,26"), Map.of(
            14, "Gạo nếp cái, mã 1001. Trong 100 g phần ăn được: năng lượng 344 kcal, protein 8,6 g, lipid 1,5 g, glucid 74,5 g.",
            26, "Bánh phở, mã 1013. Trong 100 g phần ăn được: năng lượng 143 kcal, protein 3,2 g, lipid 0,4 g, glucid 31,7 g."
        ));
        assertThat(chunks).extracting(RagPdf.Chunk::pageStart).containsOnly(3, 14, 26);
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.pageStart()).isEqualTo(3);
            assertThat(chunk.content()).contains("MỤC", "LỤC");
            assertThat(chunk.extractionMethod()).isEqualTo("PDF_TEXT");
        });
        assertThat(chunks).filteredOn(chunk -> chunk.pageStart() == 14).singleElement().satisfies(chunk -> {
            assertThat(chunk.pageEnd()).isEqualTo(14);
            assertThat(chunk.content()).contains("Gạo nếp cái", "1001", "100 g", "344 kcal", "8,6 g", "1,5 g", "74,5 g");
            assertThat(chunk.extractionMethod()).isEqualTo("VERIFIED_TRANSCRIPTION");
        });
        assertThat(chunks).filteredOn(chunk -> chunk.pageStart() == 26).singleElement().satisfies(chunk -> {
            assertThat(chunk.pageEnd()).isEqualTo(26);
            assertThat(chunk.content()).contains("Bánh phở", "1013", "100 g", "143 kcal", "3,2 g", "0,4 g", "31,7 g");
            assertThat(chunk.extractionMethod()).isEqualTo("VERIFIED_TRANSCRIPTION");
        });
    }

    @Test void US05_TC10_textless_PDF_is_reported_as_unreadable() throws Exception {
        var file = minimalPdf(false);
        assertThatThrownBy(() -> RagPdf.read(file, RagPdf.ranges(""), Map.of()))
            .isInstanceOf(RagPdf.Failure.class).hasMessage("PDF_NO_TEXT");
    }

    @Test void US05_TC10_nonempty_PDF_password_is_reported() throws Exception {
        var file = minimalPdf(true);
        assertThatThrownBy(() -> RagPdf.read(file, RagPdf.ranges(""), Map.of()))
            .isInstanceOf(RagPdf.Failure.class).hasMessage("PDF_PASSWORD_REQUIRED");
    }

    private static ByteArrayResource minimalPdf(boolean protectedFile) throws IOException {
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            if (protectedFile) document.protect(new StandardProtectionPolicy("owner", "reader", new AccessPermission()));
            var output = new ByteArrayOutputStream();
            document.save(output);
            return new ByteArrayResource(output.toByteArray()) {
                @Override public String getFilename() { return "fixture.pdf"; }
            };
        }
    }
}
