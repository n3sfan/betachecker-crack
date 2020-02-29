package idk;

import lombok.experimental.UtilityClass;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.Consumer;

@UtilityClass
public class AsmUtils implements Opcodes {

    public static boolean isNumber(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();

        if(opcode >= ICONST_M1 && opcode <= DCONST_1) {
            return true;
        }

        if(opcode == LDC && ((LdcInsnNode) insn).cst instanceof Number) {
            return true;
        }

        if(opcode >= BIPUSH && opcode <= DLOAD) {
            return true;
        }

        return false;
    }

    public static MethodNode findMethod(ClassNode cn, String name, String desc) {
        for (MethodNode method : cn.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }

        return null;
    }

    public static MethodNode cloneMethod(MethodNode method, Consumer<AbstractInsnNode> consumer) {
        MethodNode ret = new MethodNode(method.access, method.name, method.desc, method.signature, method.exceptions.toArray(new String[0]));
        ret.instructions = cloneInsnList(method.instructions, consumer);
        ret.localVariables = null; // TODO IDK
        ret.maxLocals = method.maxLocals;
        ret.maxStack = method.maxStack;
        return ret;
    }

    public static InsnList cloneInsnList(InsnList insnList, Consumer<AbstractInsnNode> consumer) {
        InsnList ret = new InsnList();
        HashMap<LabelNode, LabelNode> labels = cloneLabels(insnList);

        for (AbstractInsnNode ain : insnList) {
            AbstractInsnNode cloned = ain.clone(labels);
            if (consumer != null) consumer.accept(cloned);
            ret.add(cloned);
        }

        return ret;
    }


    private static HashMap<LabelNode, LabelNode> cloneLabels(InsnList insnList) {
        HashMap<LabelNode, LabelNode> ret = new HashMap<>();

        for (AbstractInsnNode ain : insnList) {
            if (ain.getType() == AbstractInsnNode.LABEL) {
                ret.put((LabelNode) ain, new LabelNode());
            }
        }

        return ret;
    }

    public static Class<?> load(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ASM7);
        cn.accept(cw);
        byte[] clazzBytes = cw.toByteArray();

        try(FileOutputStream out = new FileOutputStream("lib/test.class")) {
            out.write(clazzBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ClassDefiner().loadClass(cn.name.replaceAll("/", "."), clazzBytes);
    }

    private static class ClassDefiner extends ClassLoader {
        public ClassDefiner() {
            super(ClassLoader.getSystemClassLoader());
        }

        public Class<?> loadClass(String name, byte[] clazzBytes) {
            return defineClass(name, clazzBytes, 0, clazzBytes.length);
        }
    }

}
