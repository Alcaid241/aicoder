package com.ai.coder.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** calculator 工具：exp4j 表达式求值。限制长度防 DoS，非法表达式转友好错误。 */
@Slf4j
@Component
public class CalculatorTool {

    private static final int MAX_LEN = 500;

    @Tool(description = "计算器：对数学表达式求值并返回数值结果。支持 + - * / % 和括号，如 3*(2+2)。")
    public String calculator(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        String expr = expression.trim();
        if (expr.length() > MAX_LEN) {
            throw new IllegalArgumentException("表达式过长（>" + MAX_LEN + " 字符），拒绝计算");
        }
        try {
            Expression e = new ExpressionBuilder(expr).build();
            double v = e.evaluate();
            if (!Double.isFinite(v)) {
                return "结果非有限数";
            }
            // 整数结果去掉小数点（12.0 → "12"），小数保留（2.5 → "2.5"）
            if (v == Math.rint(v) && Math.abs(v) < 1e15) {
                return Long.toString((long) v);
            }
            return Double.toString(v);
        // exp4j 0.4.8 非法表达式抛 IllegalArgumentException、除零等抛 ArithmeticException，
        // 畸形括号（如 "1 + )"、"()"、")"）抛 EmptyStackException，统一兜底 RuntimeException
        } catch (RuntimeException ex) {
            log.warn("表达式求值失败：{}", expr, ex);
            throw new IllegalArgumentException("表达式无效：" + expr + "（" + ex.getMessage() + "）");
        }
    }
}
