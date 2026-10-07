package com.ai.coder.mcp.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {

    private CalculatorTool tool;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
    }

    @Test
    void calculator_evaluates_basic_arithmetic() {
        assertEquals("12", tool.calculator("3 * (2 + 2)"));
    }

    @Test
    void calculator_returns_decimal_result() {
        assertTrue(tool.calculator("10 / 4").matches("2\\.5"), "除法应给出小数结果");
    }

    @Test
    void calculator_rejects_too_long_expression() {
        String huge = "1+".repeat(2000) + "1";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.calculator(huge));
        assertTrue(ex.getMessage().contains("过长"), "应拒绝过长表达式：" + ex.getMessage());
    }

    @Test
    void calculator_rejects_invalid_expression() {
        // 注：exp4j 把 "1 + + 2" 视为 1 + (+2) = 3（一元正号，合法），
        // 故用真正的悬空运算符 "1 +" 验证非法表达式被拒绝。
        assertThrows(IllegalArgumentException.class, () -> tool.calculator("1 +"));
        assertThrows(IllegalArgumentException.class, () -> tool.calculator("foo"));
    }

    @Test
    void calculator_rejects_malformed_parens() {
        assertThrows(IllegalArgumentException.class, () -> tool.calculator("1 + )"));
    }
}
