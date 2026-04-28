package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private List<Token> tokens;
    private String input;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        this.input = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        tokens = new ArrayList<>();

        int pos = 0;
        while (pos < input.length()) {
            char ch = input.charAt(pos);

            // 跳过空白字符
            if (Character.isWhitespace(ch)) {
                pos++;
                continue;
            }

            // 识别标识符或关键字
            if (Character.isLetter(ch) || ch == '_') {
                StringBuilder sb = new StringBuilder();
                while (pos < input.length()
                        && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
                    sb.append(input.charAt(pos));
                    pos++;
                }
                String text = sb.toString();

                // 检查是否是关键字
                if (text.equals("int")) {
                    tokens.add(Token.simple("int"));
                } else if (text.equals("return")) {
                    tokens.add(Token.simple("return"));
                } else {
                    // 是标识符，加入符号表
                    if (!symbolTable.has(text)) {
                        symbolTable.add(text);
                    }
                    tokens.add(Token.normal("id", text));
                }
                continue;
            }

            // 识别整数常量
            if (Character.isDigit(ch)) {
                StringBuilder sb = new StringBuilder();
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    sb.append(input.charAt(pos));
                    pos++;
                }
                String text = sb.toString();
                tokens.add(Token.normal("IntConst", text));
                continue;
            }

            // 识别符号
            switch (ch) {
                case '=':
                    tokens.add(Token.simple("="));
                    pos++;
                    break;
                case ',':
                    tokens.add(Token.simple(","));
                    pos++;
                    break;
                case ';':
                    tokens.add(Token.simple("Semicolon"));
                    pos++;
                    break;
                case '+':
                    tokens.add(Token.simple("+"));
                    pos++;
                    break;
                case '-':
                    tokens.add(Token.simple("-"));
                    pos++;
                    break;
                case '*':
                    tokens.add(Token.simple("*"));
                    pos++;
                    break;
                case '/':
                    tokens.add(Token.simple("/"));
                    pos++;
                    break;
                case '(':
                    tokens.add(Token.simple("("));
                    pos++;
                    break;
                case ')':
                    tokens.add(Token.simple(")"));
                    pos++;
                    break;
                default:
                    throw new RuntimeException("Unexpected character: " + ch);
            }
        }

        // 添加EOF token
        tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
                path,
                StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList());
    }

}
