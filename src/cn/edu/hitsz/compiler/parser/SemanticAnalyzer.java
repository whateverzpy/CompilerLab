package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.Stack;

// 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private SymbolTable symbolTable;
    private final Stack<Token> symbolStack = new Stack<>();

    @Override
    public void whenAccept(Status currentStatus) {
        // do nothing
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final var bodySize = production.body().size();
        Token identifierToken = null;
        for (int i = 0; i < bodySize; i++) {
            final var symbol = symbolStack.pop();
            if (symbol != null && symbol.getKind().equals(TokenKind.fromString("id"))) {
                identifierToken = symbol;
            }
        }

        if (production.index() == 4) {
            if (identifierToken == null) {
                throw new RuntimeException("Missing identifier in declaration");
            }
            symbolTable.get(identifierToken.getText()).setType(SourceCodeType.Int);
        }

        symbolStack.push(null);
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        symbolStack.push(currentToken);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
        this.symbolStack.clear();
    }
}
