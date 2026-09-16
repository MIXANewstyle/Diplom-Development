package com.diplom.chatservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryFactParserTest {

    @Test
    void parsesAddUpdateDeleteAndIgnoresNoise() {
        String out = """
                Вот что я нашёл:
                + PERSON | Лера — партнёрша, отношения около двух лет
                + тема | Страх отвержения в близких отношениях
                ~ 3 | Хочет сменить работу до конца года
                - 5
                - #7
                + | без категории
                ~ abc | битая ссылка
                * не операция
                """;
        List<MemoryFactParser.Op> ops = MemoryFactParser.parse(out);

        assertThat(ops).hasSize(5);
        assertThat(ops.get(0).kind()).isEqualTo(MemoryFactParser.Kind.ADD);
        assertThat(ops.get(0).categoryId()).isEqualTo(MemoryFactParser.CAT_PERSON);
        assertThat(ops.get(0).content()).isEqualTo("Лера — партнёрша, отношения около двух лет");
        assertThat(ops.get(1).categoryId()).isEqualTo(MemoryFactParser.CAT_THEME);
        assertThat(ops.get(2).kind()).isEqualTo(MemoryFactParser.Kind.UPDATE);
        assertThat(ops.get(2).ref()).isEqualTo(3);
        assertThat(ops.get(3).kind()).isEqualTo(MemoryFactParser.Kind.DELETE);
        assertThat(ops.get(3).ref()).isEqualTo(5);
        assertThat(ops.get(4).ref()).isEqualTo(7);
    }

    @Test
    void emptyOrNullOutputIsNoOps() {
        assertThat(MemoryFactParser.parse(null)).isEmpty();
        assertThat(MemoryFactParser.parse("")).isEmpty();
        assertThat(MemoryFactParser.parse("Ничего устойчивого нет.")).isEmpty();
    }

    @Test
    void contentIsNormalizedAndCapped() {
        String longText = "x".repeat(400);
        List<MemoryFactParser.Op> ops = MemoryFactParser.parse("+ GOAL |   много    пробелов   \n+ VALUE | " + longText);
        assertThat(ops.get(0).content()).isEqualTo("много пробелов");
        assertThat(ops.get(1).content()).hasSize(MemoryFactParser.MAX_CONTENT_CHARS);
        assertThat(ops.get(1).content()).endsWith("…");
    }
}
