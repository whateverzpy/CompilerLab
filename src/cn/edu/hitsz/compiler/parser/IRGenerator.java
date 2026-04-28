package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    private SymbolTable symbolTable;
    private final Stack<IRValue> valueStack = new Stack<>();
    private final List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        if (currentToken.getKind().equals(TokenKind.fromString("id"))) {
            valueStack.push(IRVariable.named(currentToken.getText()));
        } else if (currentToken.getKind().equals(TokenKind.fromString("IntConst"))) {
            valueStack.push(IRImmediate.of(Integer.parseInt(currentToken.getText())));
        } else {
            valueStack.push(null);
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final var bodySize = production.body().size();
        final var popped = new ArrayList<IRValue>(bodySize);
        for (int i = 0; i < bodySize; i++) {
            popped.add(valueStack.pop());
        }

        switch (production.index()) {
            case 1, 2, 3, 4, 5 -> valueStack.push(null);
            case 6 -> {
                final var target = (IRVariable) popped.get(2);
                final var from = popped.get(0);
                instructions.add(Instruction.createMov(target, from));
                valueStack.push(null);
            }
            case 7 -> {
                instructions.add(Instruction.createRet(popped.get(0)));
                valueStack.push(null);
            }
            case 8 -> {
                final var result = IRVariable.temp();
                instructions.add(Instruction.createAdd(result, popped.get(2), popped.get(0)));
                valueStack.push(result);
            }
            case 9 -> {
                final var result = IRVariable.temp();
                instructions.add(Instruction.createSub(result, popped.get(2), popped.get(0)));
                valueStack.push(result);
            }
            case 10 -> valueStack.push(popped.get(0));
            case 11 -> {
                final var result = IRVariable.temp();
                instructions.add(Instruction.createMul(result, popped.get(2), popped.get(0)));
                valueStack.push(result);
            }
            case 12 -> valueStack.push(popped.get(0));
            case 13 -> valueStack.push(popped.get(1));
            case 14, 15 -> valueStack.push(popped.get(0));
            default -> throw new RuntimeException("Unknown production index: " + production.index());
        }
    }

    @Override
    public void whenAccept(Status currentStatus) {
        // do nothing
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
        this.valueStack.clear();
        this.instructions.clear();
    }

    public List<Instruction> getIR() {
        return List.copyOf(instructions);
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}
