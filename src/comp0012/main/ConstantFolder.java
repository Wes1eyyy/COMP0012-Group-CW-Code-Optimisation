package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	private Number getConstantValue(Instruction inst, ConstantPoolGen cpgen) {
		if (inst instanceof ConstantPushInstruction) {
			return ((ConstantPushInstruction) inst).getValue();
		} else if (inst instanceof LDC) {
			Object val = ((LDC) inst).getValue(cpgen);
			if (val instanceof Number) return (Number) val;
		} else if (inst instanceof LDC2_W) {
			return ((LDC2_W) inst).getValue(cpgen);
		}
		return null;
	}

	private Number foldBinaryOp(Number val1, Number val2, Instruction op) {
		if (op instanceof IADD) return val1.intValue() + val2.intValue();
		if (op instanceof ISUB) return val1.intValue() - val2.intValue();
		if (op instanceof IMUL) return val1.intValue() * val2.intValue();
		if (op instanceof IDIV) {
			if (val2.intValue() == 0) return null;
			return val1.intValue() / val2.intValue();
		}
		if (op instanceof IREM) {
			if (val2.intValue() == 0) return null;
			return val1.intValue() % val2.intValue();
		}
		if (op instanceof LADD) return val1.longValue() + val2.longValue();
		if (op instanceof LSUB) return val1.longValue() - val2.longValue();
		if (op instanceof LMUL) return val1.longValue() * val2.longValue();
		if (op instanceof LDIV) {
			if (val2.longValue() == 0L) return null;
			return val1.longValue() / val2.longValue();
		}
		if (op instanceof LREM) {
			if (val2.longValue() == 0L) return null;
			return val1.longValue() % val2.longValue();
		}
		if (op instanceof FADD) return val1.floatValue() + val2.floatValue();
		if (op instanceof FSUB) return val1.floatValue() - val2.floatValue();
		if (op instanceof FMUL) return val1.floatValue() * val2.floatValue();
		if (op instanceof FDIV) return val1.floatValue() / val2.floatValue();
		if (op instanceof FREM) return val1.floatValue() % val2.floatValue();
		if (op instanceof DADD) return val1.doubleValue() + val2.doubleValue();
		if (op instanceof DSUB) return val1.doubleValue() - val2.doubleValue();
		if (op instanceof DMUL) return val1.doubleValue() * val2.doubleValue();
		if (op instanceof DDIV) return val1.doubleValue() / val2.doubleValue();
		if (op instanceof DREM) return val1.doubleValue() % val2.doubleValue();
		if (op instanceof LCMP) {
			long l1 = val1.longValue(), l2 = val2.longValue();
			return (l1 > l2) ? 1 : (l1 == l2) ? 0 : -1;
		}
		if (op instanceof FCMPL || op instanceof FCMPG) {
			float f1 = val1.floatValue(), f2 = val2.floatValue();
			if (Float.isNaN(f1) || Float.isNaN(f2)) return (op instanceof FCMPL) ? -1 : 1;
			return (f1 > f2) ? 1 : (f1 == f2) ? 0 : -1;
		}
		if (op instanceof DCMPL || op instanceof DCMPG) {
			double d1 = val1.doubleValue(), d2 = val2.doubleValue();
			if (Double.isNaN(d1) || Double.isNaN(d2)) return (op instanceof DCMPL) ? -1 : 1;
			return (d1 > d2) ? 1 : (d1 == d2) ? 0 : -1;
		}
		return null;
	}

	private Instruction createPushInstruction(Number value, Instruction op, ConstantPoolGen cpgen) {
		if (op instanceof IADD || op instanceof ISUB || op instanceof IMUL ||
			op instanceof IDIV || op instanceof IREM ||
			op instanceof LCMP || op instanceof FCMPL || op instanceof FCMPG ||
			op instanceof DCMPL || op instanceof DCMPG) {
			return pushInt(value.intValue(), cpgen);
		}
		if (op instanceof LADD || op instanceof LSUB || op instanceof LMUL ||
			op instanceof LDIV || op instanceof LREM) {
			return pushLong(value.longValue(), cpgen);
		}
		if (op instanceof FADD || op instanceof FSUB || op instanceof FMUL ||
			op instanceof FDIV || op instanceof FREM) {
			return pushFloat(value.floatValue(), cpgen);
		}
		if (op instanceof DADD || op instanceof DSUB || op instanceof DMUL ||
			op instanceof DDIV || op instanceof DREM) {
			return pushDouble(value.doubleValue(), cpgen);
		}
		return null;
	}

	private Instruction pushInt(int value, ConstantPoolGen cpgen) {
		if (value >= -1 && value <= 5) return new ICONST(value);
		if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new BIPUSH((byte) value);
		if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new SIPUSH((short) value);
		return new LDC(cpgen.addInteger(value));
	}

	private Instruction pushLong(long value, ConstantPoolGen cpgen) {
		if (value == 0L) return new LCONST(0);
		if (value == 1L) return new LCONST(1);
		return new LDC2_W(cpgen.addLong(value));
	}

	private Instruction pushFloat(float value, ConstantPoolGen cpgen) {
		return new LDC(cpgen.addFloat(value));
	}

	private Instruction pushDouble(double value, ConstantPoolGen cpgen) {
		return new LDC2_W(cpgen.addDouble(value));
	}

	private void safeDelete(InstructionList il, InstructionHandle from, InstructionHandle to, InstructionHandle redirectTo) {
		try {
			il.delete(from, to);
		} catch (TargetLostException e) {
			for (InstructionHandle target : e.getTargets()) {
				for (InstructionTargeter targeter : target.getTargeters()) {
					targeter.updateTarget(target, redirectTo);
				}
			}
		}
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			if (method.getCode() == null) continue;

			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();

			boolean changed = true;
			int iterations = 0;
			while (changed && iterations < 100) {
				changed = false;
				if (propagateConstantVariables(il, cpgen)) changed = true;
				if (propagateDynamicVariables(il, cpgen)) changed = true;
				if (foldConstants(il, cpgen)) changed = true;
				iterations++;
			}

			mg.removeCodeAttributes();
			mg.setMaxStack();
			mg.setMaxLocals();
			cgen.replaceMethod(method, mg.getMethod());
		}

		this.optimized = cgen.getJavaClass();
	}

	// SUBGOAL 2: propagate variables assigned exactly once with a constant value
	private boolean propagateConstantVariables(InstructionList il, ConstantPoolGen cpgen) {
		boolean changed = false;
		InstructionHandle[] handles = il.getInstructionHandles();

		Map<Integer, Integer> storeCount = new HashMap<>();
		Map<Integer, Number> storeValue = new HashMap<>();

		for (int i = 0; i < handles.length; i++) {
			Instruction inst = handles[i].getInstruction();

			if (inst instanceof StoreInstruction) {
				int index = ((StoreInstruction) inst).getIndex();
				storeCount.put(index, storeCount.getOrDefault(index, 0) + 1);

				if (i > 0) {
					Number val = getConstantValue(handles[i - 1].getInstruction(), cpgen);
					if (val != null) {
						storeValue.put(index, val);
					} else {
						storeValue.remove(index);
					}
				}
			}

			if (inst instanceof IINC) {
				int index = ((IINC) inst).getIndex();
				storeCount.put(index, storeCount.getOrDefault(index, 0) + 2);
			}
		}

		for (Map.Entry<Integer, Integer> entry : storeCount.entrySet()) {
			int varIndex = entry.getKey();
			if (entry.getValue() != 1 || !storeValue.containsKey(varIndex)) continue;

			Number constVal = storeValue.get(varIndex);

			Instruction storeInst = null;
			for (InstructionHandle h : handles) {
				if (h.getInstruction() instanceof StoreInstruction &&
					((StoreInstruction) h.getInstruction()).getIndex() == varIndex) {
					storeInst = h.getInstruction();
					break;
				}
			}
			if (storeInst == null) continue;

			for (InstructionHandle h : il.getInstructionHandles()) {
				if (h.getInstruction() instanceof LoadInstruction &&
					((LoadInstruction) h.getInstruction()).getIndex() == varIndex) {
					h.setInstruction(createTypedPushInstruction(constVal, storeInst, cpgen));
					changed = true;
				}
			}
		}

		return changed;
	}

	private Instruction createTypedPushInstruction(Number value, Instruction storeInst, ConstantPoolGen cpgen) {
		if (storeInst instanceof ISTORE) return pushInt(value.intValue(), cpgen);
		if (storeInst instanceof LSTORE) return pushLong(value.longValue(), cpgen);
		if (storeInst instanceof FSTORE) return pushFloat(value.floatValue(), cpgen);
		if (storeInst instanceof DSTORE) return pushDouble(value.doubleValue(), cpgen);
		return null;
	}

	// SUBGOAL 3: propagate variables reassigned multiple times, within each constant-value interval
	private boolean propagateDynamicVariables(InstructionList il, ConstantPoolGen cpgen) {
		boolean changed = false;
		InstructionHandle[] handles = il.getInstructionHandles();

		// For each branch, variables modified between the branch and its target must be
		// invalidated at the target (they could differ across paths, e.g. inside a loop)
		Map<InstructionHandle, Set<Integer>> invalidateAtTarget = new HashMap<>();
		for (InstructionHandle h : handles) {
			Instruction inst = h.getInstruction();
			if (inst instanceof BranchInstruction) {
				InstructionHandle target = ((BranchInstruction) inst).getTarget();
				collectModifiedVarsBetween(h, target, invalidateAtTarget);
				if (inst instanceof Select) {
					for (InstructionHandle t : ((Select) inst).getTargets()) {
						collectModifiedVarsBetween(h, t, invalidateAtTarget);
					}
				}
			}
		}

		Map<Integer, Number> varValues = new HashMap<>();
		Map<Integer, Instruction> varStoreType = new HashMap<>();

		for (InstructionHandle h : handles) {
			Instruction inst = h.getInstruction();

			if (invalidateAtTarget.containsKey(h)) {
				for (int idx : invalidateAtTarget.get(h)) {
					varValues.remove(idx);
					varStoreType.remove(idx);
				}
			}

			if (inst instanceof LoadInstruction) {
				int index = ((LoadInstruction) inst).getIndex();
				if (varValues.containsKey(index) && varStoreType.containsKey(index)) {
					Instruction push = createTypedPushInstruction(varValues.get(index), varStoreType.get(index), cpgen);
					if (push != null) {
						h.setInstruction(push);
						changed = true;
					}
				}
			}

			if (inst instanceof StoreInstruction) {
				int index = ((StoreInstruction) inst).getIndex();
				InstructionHandle prev = h.getPrev();
				if (prev != null) {
					Number val = getConstantValue(prev.getInstruction(), cpgen);
					if (val != null) {
						varValues.put(index, val);
						varStoreType.put(index, inst);
					} else {
						varValues.remove(index);
						varStoreType.remove(index);
					}
				} else {
					varValues.remove(index);
					varStoreType.remove(index);
				}
			}

			if (inst instanceof IINC) {
				int index = ((IINC) inst).getIndex();
				varValues.remove(index);
				varStoreType.remove(index);
			}
		}

		return changed;
	}

	private void collectModifiedVarsBetween(InstructionHandle branchHandle, InstructionHandle target,
											Map<InstructionHandle, Set<Integer>> invalidateAtTarget) {
		InstructionHandle start, end;
		if (target.getPosition() <= branchHandle.getPosition()) {
			start = target;
			end = branchHandle;
		} else {
			start = branchHandle;
			end = target;
		}

		Set<Integer> modifiedVars = new HashSet<>();
		for (InstructionHandle scan = start; scan != null && scan != end.getNext(); scan = scan.getNext()) {
			if (scan.getInstruction() instanceof StoreInstruction) {
				modifiedVars.add(((StoreInstruction) scan.getInstruction()).getIndex());
			}
			if (scan.getInstruction() instanceof IINC) {
				modifiedVars.add(((IINC) scan.getInstruction()).getIndex());
			}
		}

		if (!modifiedVars.isEmpty()) {
			invalidateAtTarget.merge(target, modifiedVars, (a, b) -> { a.addAll(b); return a; });
		}
	}

	// SUBGOAL 1: fold (const, const, arithmetic_op) triplets into a single constant
	private boolean foldConstants(InstructionList il, ConstantPoolGen cpgen) {
		boolean anyChanged = false;
		boolean changed = true;

		while (changed) {
			changed = false;
			InstructionHandle[] handles = il.getInstructionHandles();

			for (int i = 0; i < handles.length - 2; i++) {
				Number val1 = getConstantValue(handles[i].getInstruction(), cpgen);
				if (val1 == null) continue;

				Number val2 = getConstantValue(handles[i + 1].getInstruction(), cpgen);
				if (val2 == null) continue;

				Instruction arithInst = handles[i + 2].getInstruction();
				Number result = foldBinaryOp(val1, val2, arithInst);
				if (result == null) continue;

				Instruction newInst = createPushInstruction(result, arithInst, cpgen);
				if (newInst == null) continue;

				handles[i].setInstruction(newInst);
				safeDelete(il, handles[i + 1], handles[i + 2], handles[i]);
				changed = true;
				anyChanged = true;
				break;
			}
		}
		return anyChanged;
	}


	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
