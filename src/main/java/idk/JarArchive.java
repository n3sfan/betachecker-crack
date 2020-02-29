package idk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.*;
import java.util.zip.ZipEntry;

public class JarArchive {

    public final Map<String, ClassNode> classes;
    public final Map<String, byte[]> others;
    public final String input;
    private final Map<String, ZipEntry> entries;
    private Manifest manifest;

    public JarArchive(String input) throws IOException {
        this.input = input;
        this.classes = new HashMap<>();
        this.others = new HashMap<>();
        entries = new HashMap<>();
    }

    public void load() throws IOException {
        try (JarInputStream in = new JarInputStream(new FileInputStream(input))) {
            this.manifest = in.getManifest();

            ZipEntry ze;
            int nread;
            byte[] buffer = new byte[4096];

            while ((ze = in.getNextEntry()) != null) {
                String name = ze.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                while ((nread = in.read(buffer, 0, buffer.length)) > 0) {
                    baos.write(buffer, 0, nread);
                }

                byte[] data = baos.toByteArray();

                if (name.endsWith(".class")) {
                    ClassReader cr = new ClassReader(data);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    classes.put(cn.name, cn);
                } else {
                    others.put(name, data);
                }

                entries.put(name, ze);
            }
        }
    }

    public void save(String outputFile) throws IOException, NoSuchFieldException, IllegalAccessException {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(outputFile))) {
            if (manifest != null) {
                out.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
                manifest.write(out);
            }

            for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
                String name = entry.getKey() + ".class";
                ClassNode cn = entry.getValue();
                ClassWriter cw = new ClassWriter(0);
                cn.accept(cw);

                JarEntry je = new JarEntry(name);
                copyEntry(entries.get(name), je);
                out.putNextEntry(je);
                out.write(cw.toByteArray());
            }

            for (Map.Entry<String, byte[]> entry : others.entrySet()) {
                String name = entry.getKey();
                byte[] data = entry.getValue();
                JarEntry je = new JarEntry(name);
                copyEntry(entries.get(name), je);
                out.putNextEntry(je);
                out.write(data);
            }
        }
    }

    private void copyEntry(ZipEntry from, ZipEntry to) throws NoSuchFieldException, IllegalAccessException {
        if (from instanceof JarEntry && to instanceof JarEntry) {
            copyFieldValue(from, to, "attr");
            copyFieldValue(from, to, "certs");
            copyFieldValue(from, to, "signers");
        }

        to.setExtra(from.getExtra());
        to.setComment(from.getComment());
        to.setMethod(from.getMethod());
    }

    private void copyFieldValue(Object fromObj, Object toObj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Field fField = fromObj.getClass().getDeclaredField(fieldName);
        Field tField = toObj.getClass().getDeclaredField(fieldName);
        fField.setAccessible(true);
        tField.setAccessible(true);
        tField.set(toObj, fField.get(fromObj));
    }

}
