package idk;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;

public class Skidder implements Opcodes {

    private static JarArchive archive;

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {
        archive = new JarArchive("lib/BetaChecker/BetaChecker.jar");
        archive.load();
        modify();
        archive.save("lib/BetaChecker/BetaChecker_cracked.jar");
    }

    private static void modify() {
        for (Map.Entry<String, ClassNode> entry : archive.classes.entrySet()) {
            String name = entry.getKey();
            ClassNode cn = entry.getValue();

            if (name.equals("a/M")) {
                preModify(cn);
                postModify(cn);
            }
        }
    }

    private static void preModify(ClassNode cn) {
        if (cn.name.equals("a/M")) {
            for (MethodNode method : cn.methods) {
                InsnList insnList = method.instructions;

                for (AbstractInsnNode ain : insnList) {
                    if (ain.getOpcode() == INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) ain;

                        if (min.owner.equals(cn.name) && min.name.equals("a")
                                && min.desc.equals("(II)Ljava/lang/String;")) {
                            String s = deobString(cn, min);

                            if (s != null) {
                                insnList.insertBefore(ain, new InsnNode(POP2));
                                insnList.set(ain, new LdcInsnNode(s));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void postModify(ClassNode cn) {
        if (cn.name.equals("a/M")) {
            LabelNode startLabel = new LabelNode();

            for (MethodNode method : cn.methods) {
                InsnList insnList = method.instructions;

                for (AbstractInsnNode ain : insnList) {
                    if (ain.getOpcode() == INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) ain;

                        if (min.owner.equals("a/R") && min.name.equals("c") && min.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                            String s = deobStringDecrypt(min);

                            if (s != null) {
                                ((LdcInsnNode) min.getPrevious()).cst = s;
                                insnList.remove(min);
                            }
                        }
                    }
                }

                for (AbstractInsnNode ain : insnList) {
                    if (ain.getOpcode() == LDC) {
                        LdcInsnNode lin = (LdcInsnNode) ain;

                        if (lin.cst.equals("License is valid!")) {
                            InsnList temp = new InsnList();
                            temp.add(startLabel);
                            temp.add(new FrameNode(F_SAME, 0, null, 0, new Object[]{"java/lang/String"}));
                            temp.add(new InsnNode(ICONST_1));
                            temp.add(new VarInsnNode(ISTORE, 2));
                            insnList.insertBefore(lin, temp);
                        } else if (lin.cst.equals("Checking your license key...")) {
                            insnList.insert(lin.getNext(), new JumpInsnNode(GOTO, startLabel));
                        }
                    }
                }
            }
        }
    }

    private static String deobStringDecrypt(MethodInsnNode min) {
        if (min.getPrevious().getOpcode() != LDC)
            return null;

        ClassNode deob = archive.classes.get(min.owner);

        for (MethodNode method : deob.methods) {
            if (method.desc.equals("(Lorg/e;)Lorg/e;")) {
                method.desc = "(Ljava/lang/Exception;)Ljava/lang/Exception;";
            }

            for (AbstractInsnNode ain : method.instructions) {
                if (ain.getType() == AbstractInsnNode.FRAME) {
                    FrameNode fn = (FrameNode) ain;
                    if (fn.stack != null && fn.stack.size() == 1 && fn.stack.get(0).equals("org/e")) {
                        fn.stack.clear();
                        fn.stack.add("java/lang/Exception");
                    }
                } else if (ain.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode min1 = (MethodInsnNode) ain;

                    if (min1.desc.equals("(Lorg/e;)Lorg/e;")) {
                        min1.desc = "(Ljava/lang/Exception;)Ljava/lang/Exception;";
                    }
                }
            }

            for (TryCatchBlockNode tcbn : method.tryCatchBlocks) {
                if (tcbn.type.equals("org/e")) {
                    tcbn.type = "java/lang/Exception";
                }
            }
        }

        Class<?> clazz = AsmUtils.load(archive.classes.get(min.owner));

        try {
            Method method = clazz.getDeclaredMethod(min.name, String.class);
            String s = method.invoke(null, ((LdcInsnNode) min.getPrevious()).cst.toString()).toString();
            System.out.println(s);
            return s;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String deobString(ClassNode cn, MethodInsnNode min) {
        int nums = 0;
        InsnList insnList = new InsnList();
        insnList.insert(min.clone(new HashMap<>()));
        AbstractInsnNode ain = min;

        while (nums < 2) {
            if (ain == null)
                break;

            if (AsmUtils.isNumber(ain)) {
                nums++;
                insnList.insert(ain.clone(new HashMap<>()));
            }

            ain = ain.getPrevious();
        }

        if (nums < 2)
            return null;

        insnList.add(new InsnNode(ARETURN));
        Class<?> deobClazz = generateDeobClass(insnList, cn, min);

        try {
            Method method = deobClazz.getDeclaredMethod("deob_string");
            String s = method.invoke(null).toString();
            System.out.println(s);
            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Class<?> generateDeobClass(InsnList insnList, ClassNode cn, MethodInsnNode min) {
        for (AbstractInsnNode ain : insnList) {
            if (ain.getOpcode() == INVOKESTATIC) {
                ((MethodInsnNode) ain).owner = "obfuscated_string";
            }
        }

        ClassNode deob = new ClassNode();
        deob.name = "obfuscated_string";
        deob.superName = "java/lang/Object";
        deob.access = ACC_PUBLIC;
        deob.version = V1_8;

        // fields
       /* deob.fields.add(new FieldNode(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "c",
                "[Ljava/lang/String;", null, null));
        deob.fields.add(new FieldNode(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "d",
                "[Ljava/lang/String;", null, null));*/

        // deob_string method
        MethodNode deobMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, "deob_string",
                "()Ljava/lang/String;", null, null);
        deobMethod.instructions = insnList;
        // try-catch in deob_string method
        LabelNode start1 = new LabelNode(), end1 = new LabelNode();

        deobMethod.tryCatchBlocks.add(new TryCatchBlockNode(start1, end1, end1,
                "java/lang/Exception"));
        deobMethod.instructions.insert(start1);
        deobMethod.instructions.add(end1);
        deobMethod.instructions.add(new FrameNode(F_SAME1, 0, null, 1,
                new Object[]{"java/lang/Exception"}));
        deobMethod.instructions.add(new VarInsnNode(ASTORE, 0));
        deobMethod.instructions.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
        deobMethod.instructions.add(new VarInsnNode(ALOAD, 0));
        deobMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Exception",
                "toString", "()Ljava/lang/String;", false));
        deobMethod.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(Ljava/lang/String;)V", false));
        deobMethod.instructions.add(new LdcInsnNode("THIS STRING IS FREAKING WEIRD CANNOT DEOB!"));
        deobMethod.instructions.add(new InsnNode(ARETURN));
        // maxs
        deobMethod.maxLocals = 1;
        deobMethod.maxStack = 2;
        deob.methods.add(deobMethod);


        Consumer<AbstractInsnNode> consumer = ain -> {
            switch (ain.getType()) {
                case AbstractInsnNode.FIELD_INSN:
                    FieldInsnNode fin = (FieldInsnNode) ain;
                    if (fin.owner.equals(cn.name)) fin.owner = deob.name;
                    break;
                case AbstractInsnNode.METHOD_INSN:
                    MethodInsnNode min1 = (MethodInsnNode) ain;
                    if (min1.owner.equals(cn.name)) min1.owner = deob.name;
                    break;
                default:
                    break;
            }
        };
        deob.methods.add(scanAndAddFields(deob,
                AsmUtils.cloneMethod(AsmUtils.findMethod(cn, min.name, min.desc), consumer)));

        MethodNode clinit = AsmUtils.cloneMethod(AsmUtils.findMethod(cn, "<clinit>", "()V"), consumer);
        boolean skip = false;

        for (AbstractInsnNode ain : clinit.instructions) {
            if (ain.getOpcode() == ANEWARRAY && ((TypeInsnNode) ain).desc.equals("com/github/steveice10/mc/protocol/packet/MinecraftPacket")) {
                clinit.instructions.remove(ain.getPrevious());
                clinit.instructions.remove(ain);
            } else if (ain.getOpcode() == INVOKESTATIC && ((MethodInsnNode) ain).desc.equals("([Lcom/github/steveice10/mc/protocol/packet/MinecraftPacket;)V")) {
                clinit.instructions.remove(ain);
            } else if (ain.getOpcode() == NEW) {
                TypeInsnNode tin = (TypeInsnNode) ain;
                if (tin.desc.equals("org/eV") || tin.desc.equals("org/ev")) {
                    clinit.instructions.remove(ain);
                    skip = true;
                }
            } else if (skip) {
                if (ain.getOpcode() == RETURN) {
                    skip = false;
                    break;
                } else {
                    clinit.instructions.remove(ain);
                }
            }
        }

        deob.methods.add(scanAndAddFields(deob, clinit));
        return AsmUtils.load(deob);
    }

    private static MethodNode scanAndAddFields(ClassNode cn, MethodNode method) {
        for (AbstractInsnNode ain : method.instructions) {
            if (ain.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fin = (FieldInsnNode) ain;

                if (fin.owner.equals(cn.name) && cn.fields.stream().noneMatch(fieldNode -> fieldNode.name.equals(fin.name))) {
                    cn.fields.add(new FieldNode(ACC_PRIVATE | ACC_FINAL
                            | ((fin.getOpcode() == GETSTATIC || fin.getOpcode() == PUTSTATIC) ? ACC_STATIC : 1),
                            fin.name, fin.desc, null, null));
                }
            }
        }

        return method;
    }

    private static void copyEntry(JarEntry from, JarEntry to) throws NoSuchFieldException, IllegalAccessException {
        copyFieldValue(from, to, "attr");
        copyFieldValue(from, to, "certs");
        copyFieldValue(from, to, "signers");
        to.setExtra(from.getExtra());
        to.setComment(from.getComment());
        to.setMethod(from.getMethod());
    }

    private static void copyFieldValue(Object fromObj, Object toObj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Field fField = fromObj.getClass().getDeclaredField(fieldName);
        Field tField = toObj.getClass().getDeclaredField(fieldName);
        fField.setAccessible(true);
        tField.setAccessible(true);
        tField.set(toObj, fField.get(fromObj));
    }

}
