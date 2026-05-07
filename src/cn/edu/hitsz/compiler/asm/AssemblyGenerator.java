package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 实验四: 汇编生成与寄存器分配.
 */
public class AssemblyGenerator {
    private static final List<String> REGISTERS = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");

    private final List<Instruction> instructions = new ArrayList<>();
    private final Map<IRVariable, Integer> lastUsage = new HashMap<>();
    private final Map<IRVariable, Integer> stackOffset = new LinkedHashMap<>();
    private final Map<IRVariable, String> varToReg = new HashMap<>();
    private final Map<String, IRVariable> regToVar = new HashMap<>();
    private final List<String> assembly = new ArrayList<>();

    private int frameSize = 0;

    /**
     * 加载前端提供的中间代码, 并生成规范化 IR、变量最后使用位置、栈槽等信息.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        instructions.clear();
        lastUsage.clear();
        stackOffset.clear();
        varToReg.clear();
        regToVar.clear();
        assembly.clear();

        normalize(originInstructions);
        collectVariableInfo();
    }


    /**
     * 顺序扫描 IR, 同步完成寄存器分配与目标代码生成.
     */
    public void run() {
        assembly.clear();
        varToReg.clear();
        regToVar.clear();

        assembly.add(".text");
        emitStackAdjust(-frameSize);

        for (int i = 0; i < instructions.size(); i++) {
            final var instruction = instructions.get(i);
            switch (instruction.getKind()) {
                case MOV -> generateMov(instruction, i);
                case ADD, SUB, MUL -> generateBinary(instruction, i);
                case RET -> generateRet(instruction, i);
            }
        }
    }


    /**
     * 输出汇编代码到文件.
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(path, assembly);
    }

    private void normalize(List<Instruction> originInstructions) {
        for (final var instruction : originInstructions) {
            if (instruction.getKind().isReturn()) {
                instructions.add(instruction);
                break;
            }

            if (!instruction.getKind().isBinary()) {
                instructions.add(instruction);
                continue;
            }

            final var result = instruction.getResult();
            final var lhs = instruction.getLHS();
            final var rhs = instruction.getRHS();
            final var kind = instruction.getKind();

            if (lhs instanceof IRImmediate leftImmediate && rhs instanceof IRImmediate rightImmediate) {
                instructions.add(Instruction.createMov(result, IRImmediate.of(evaluate(kind,
                    leftImmediate.getValue(), rightImmediate.getValue()))));
            } else if (kind == InstructionKind.ADD && lhs instanceof IRImmediate && rhs instanceof IRVariable) {
                instructions.add(Instruction.createAdd(result, rhs, lhs));
            } else if (kind == InstructionKind.SUB && lhs instanceof IRImmediate) {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createMov(temp, lhs));
                instructions.add(Instruction.createSub(result, temp, rhs));
            } else if (kind == InstructionKind.MUL && lhs instanceof IRImmediate) {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createMov(temp, lhs));
                instructions.add(Instruction.createMul(result, temp, rhs));
            } else if (kind == InstructionKind.MUL && rhs instanceof IRImmediate) {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createMov(temp, rhs));
                instructions.add(Instruction.createMul(result, lhs, temp));
            } else {
                instructions.add(instruction);
            }
        }
    }

    private int evaluate(InstructionKind kind, int lhs, int rhs) {
        return switch (kind) {
            case ADD -> lhs + rhs;
            case SUB -> lhs - rhs;
            case MUL -> lhs * rhs;
            default -> throw new RuntimeException("Can not fold instruction kind: " + kind);
        };
    }

    private void collectVariableInfo() {
        for (int i = 0; i < instructions.size(); i++) {
            final var instruction = instructions.get(i);

            if (instruction.getKind().isBinary() || instruction.getKind().isUnary()) {
                ensureStackSlot(instruction.getResult());
            }

            for (final var operand : instruction.getOperands()) {
                if (operand instanceof IRVariable variable) {
                    ensureStackSlot(variable);
                    lastUsage.put(variable, i);
                }
            }
        }

        frameSize = stackOffset.size() * 4;
    }

    private void ensureStackSlot(IRVariable variable) {
        if (!stackOffset.containsKey(variable)) {
            stackOffset.put(variable, stackOffset.size() * 4);
        }
    }

    private void generateMov(Instruction instruction, int index) {
        final var from = instruction.getFrom();
        final var result = instruction.getResult();
        final var protectedRegs = new HashSet<String>();

        if (from instanceof IRImmediate immediate) {
            final var destReg = allocateForWrite(result, protectedRegs);
            emit("li %s, %d".formatted(destReg, immediate.getValue()), instruction);
            finishWrite(result, destReg);
        } else {
            final var srcReg = loadValue(from, protectedRegs);
            final var destReg = result.equals(from) ? srcReg : allocateForWrite(result, protectedRegs);
            emit("mv %s, %s".formatted(destReg, srcReg), instruction);
            finishWrite(result, destReg);
        }

        releaseDeadOperands(instruction, index);
        releaseIfDead(result, index);
    }

    private void generateBinary(Instruction instruction, int index) {
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();
        final var result = instruction.getResult();
        final var protectedRegs = new HashSet<String>();

        final var lhsReg = loadValue(lhs, protectedRegs);

        switch (instruction.getKind()) {
            case ADD -> {
                if (rhs instanceof IRImmediate immediate && fitsSigned12(immediate.getValue())) {
                    final var destReg = result.equals(lhs) ? lhsReg : allocateForWrite(result, protectedRegs);
                    emit("addi %s, %s, %d".formatted(destReg, lhsReg, immediate.getValue()), instruction);
                    finishWrite(result, destReg);
                } else {
                    final var rhsReg = loadValue(rhs, protectedRegs);
                    final var destReg = result.equals(lhs) ? lhsReg
                        : result.equals(rhs) ? rhsReg
                        : allocateForWrite(result, protectedRegs);
                    emit("add %s, %s, %s".formatted(destReg, lhsReg, rhsReg), instruction);
                    finishWrite(result, destReg);
                }
            }
            case SUB -> {
                if (rhs instanceof IRImmediate immediate && fitsSigned12(-immediate.getValue())) {
                    final var destReg = result.equals(lhs) ? lhsReg : allocateForWrite(result, protectedRegs);
                    emit("addi %s, %s, %d".formatted(destReg, lhsReg, -immediate.getValue()), instruction);
                    finishWrite(result, destReg);
                } else {
                    final var rhsReg = loadValue(rhs, protectedRegs);
                    final var destReg = result.equals(lhs) ? lhsReg
                        : result.equals(rhs) ? rhsReg
                        : allocateForWrite(result, protectedRegs);
                    emit("sub %s, %s, %s".formatted(destReg, lhsReg, rhsReg), instruction);
                    finishWrite(result, destReg);
                }
            }
            case MUL -> {
                final var rhsReg = loadValue(rhs, protectedRegs);
                final var destReg = result.equals(lhs) ? lhsReg
                    : result.equals(rhs) ? rhsReg
                    : allocateForWrite(result, protectedRegs);
                emit("mul %s, %s, %s".formatted(destReg, lhsReg, rhsReg), instruction);
                finishWrite(result, destReg);
            }
            default -> throw new RuntimeException("Unknown binary instruction kind: " + instruction.getKind());
        }

        releaseDeadOperands(instruction, index);
        releaseIfDead(result, index);
    }

    private void generateRet(Instruction instruction, int index) {
        final var returnValue = instruction.getReturnValue();
        final var protectedRegs = new HashSet<String>();

        if (returnValue instanceof IRImmediate immediate) {
            emit("li a0, %d".formatted(immediate.getValue()), instruction);
        } else {
            final var retReg = loadValue(returnValue, protectedRegs);
            emit("mv a0, %s".formatted(retReg), instruction);
        }

        releaseDeadOperands(instruction, index);
        emitStackAdjust(frameSize);
    }

    private String loadValue(IRValue value, Set<String> protectedRegs) {
        if (value instanceof IRImmediate immediate) {
            final var reg = acquireRegister(protectedRegs);
            emit("li %s, %d".formatted(reg, immediate.getValue()), null);
            protectedRegs.add(reg);
            return reg;
        }

        final var variable = (IRVariable) value;
        if (varToReg.containsKey(variable)) {
            final var reg = varToReg.get(variable);
            protectedRegs.add(reg);
            return reg;
        }

        final var reg = acquireRegister(protectedRegs);
        emit("lw %s, %d(sp)".formatted(reg, stackOffset.get(variable)), null);
        bind(variable, reg);
        protectedRegs.add(reg);
        return reg;
    }

    private String allocateForWrite(IRVariable variable, Set<String> protectedRegs) {
        if (varToReg.containsKey(variable)) {
            final var reg = varToReg.get(variable);
            protectedRegs.add(reg);
            return reg;
        }

        final var reg = acquireRegister(protectedRegs);
        bind(variable, reg);
        protectedRegs.add(reg);
        return reg;
    }

    private String acquireRegister(Set<String> protectedRegs) {
        for (final var reg : REGISTERS) {
            if (!regToVar.containsKey(reg) && !protectedRegs.contains(reg)) {
                return reg;
            }
        }

        for (final var reg : REGISTERS) {
            final var variable = regToVar.get(reg);
            if (!protectedRegs.contains(reg) && !isLive(variable)) {
                unbind(reg);
                return reg;
            }
        }

        for (final var reg : REGISTERS) {
            if (!protectedRegs.contains(reg)) {
                unbind(reg);
                return reg;
            }
        }

        throw new RuntimeException("No available register");
    }

    private boolean isLive(IRVariable variable) {
        return lastUsage.containsKey(variable);
    }

    private void bind(IRVariable variable, String reg) {
        unbind(reg);
        if (varToReg.containsKey(variable)) {
            regToVar.remove(varToReg.get(variable));
        }
        varToReg.put(variable, reg);
        regToVar.put(reg, variable);
    }

    private void unbind(String reg) {
        final var oldVariable = regToVar.remove(reg);
        if (oldVariable != null) {
            varToReg.remove(oldVariable);
        }
    }

    private void finishWrite(IRVariable variable, String reg) {
        bind(variable, reg);
        emit("sw %s, %d(sp)".formatted(reg, stackOffset.get(variable)), null);
    }

    private void releaseDeadOperands(Instruction instruction, int index) {
        for (final var operand : instruction.getOperands()) {
            if (operand instanceof IRVariable variable) {
                releaseIfDead(variable, index);
            }
        }
    }

    private void releaseIfDead(IRVariable variable, int index) {
        final var last = lastUsage.get(variable);
        if (last != null && last <= index) {
            final var reg = varToReg.remove(variable);
            if (reg != null) {
                regToVar.remove(reg);
            }
            lastUsage.remove(variable);
        } else if (last == null && varToReg.containsKey(variable)) {
            final var reg = varToReg.remove(variable);
            regToVar.remove(reg);
        }
    }

    private boolean fitsSigned12(int value) {
        return value >= -2048 && value <= 2047;
    }

    private void emitStackAdjust(int offset) {
        if (offset == 0) {
            return;
        }

        if (fitsSigned12(offset)) {
            assembly.add("    addi sp, sp, %d".formatted(offset));
        } else {
            assembly.add("    li t0, %d".formatted(offset));
            assembly.add("    add sp, sp, t0");
        }
    }

    private void emit(String code, Instruction instruction) {
        if (instruction == null) {
            assembly.add("    " + code);
        } else {
            assembly.add("    %s\t\t#  %s".formatted(code, instruction));
        }
    }
}
