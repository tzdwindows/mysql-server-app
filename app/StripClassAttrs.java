import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Strips JDK 21 MethodParameters / InnerClasses / EnclosingMethod attributes with null names
 * that trigger a NullPointerException in Android build-tools 34.0.0 d8 (R8 8.2.2).
 */
public class StripClassAttrs {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        processDir(dir);
    }

    private static void processDir(File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                processDir(f);
            } else if (f.getName().endsWith(".class")) {
                stripFile(f);
            }
        }
    }

    private static void stripFile(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            in.readFully(data);
        }
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(data));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        DataOutputStream out = new DataOutputStream(bos);

        out.writeInt(in.readInt()); // magic
        out.writeShort(in.readUnsignedShort()); // minor
        out.writeShort(in.readUnsignedShort()); // major

        int cpCount = in.readUnsignedShort();
        out.writeShort(cpCount);
        String[] utf8 = new String[cpCount];
        for (int i = 1; i < cpCount; i++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1: { // Utf8
                    int len = in.readUnsignedShort();
                    byte[] b = new byte[len];
                    in.readFully(b);
                    out.writeShort(len);
                    out.write(b);
                    utf8[i] = new String(b, StandardCharsets.UTF_8);
                    break;
                }
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18: {
                    out.writeInt(in.readInt());
                    break;
                }
                case 5:
                case 6: {
                    out.writeLong(in.readLong());
                    i++;
                    break;
                }
                case 7:
                case 8:
                case 16:
                case 19:
                case 20: {
                    out.writeShort(in.readUnsignedShort());
                    break;
                }
                case 15: {
                    out.writeByte(in.readUnsignedByte());
                    out.writeShort(in.readUnsignedShort());
                    break;
                }
            }
        }

        out.writeShort(in.readUnsignedShort()); // access_flags
        out.writeShort(in.readUnsignedShort()); // this_class
        out.writeShort(in.readUnsignedShort()); // super_class

        int ifCount = in.readUnsignedShort();
        out.writeShort(ifCount);
        for (int i = 0; i < ifCount; i++) {
            out.writeShort(in.readUnsignedShort());
        }

        // fields
        int fCount = in.readUnsignedShort();
        out.writeShort(fCount);
        for (int i = 0; i < fCount; i++) {
            copyMember(in, out, utf8);
        }

        // methods
        int mCount = in.readUnsignedShort();
        out.writeShort(mCount);
        for (int i = 0; i < mCount; i++) {
            copyMember(in, out, utf8);
        }

        // class attributes
        copyAttributes(in, out, utf8);

        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bos.toByteArray());
        }
    }

    private static void copyMember(DataInputStream in, DataOutputStream out, String[] utf8) throws Exception {
        out.writeShort(in.readUnsignedShort()); // access
        out.writeShort(in.readUnsignedShort()); // name
        out.writeShort(in.readUnsignedShort()); // desc
        copyAttributes(in, out, utf8);
    }

    private static void copyAttributes(DataInputStream in, DataOutputStream out, String[] utf8) throws Exception {
        int aCount = in.readUnsignedShort();
        ByteArrayOutputStream attrBos = new ByteArrayOutputStream();
        DataOutputStream attrOut = new DataOutputStream(attrBos);
        int kept = 0;
        for (int i = 0; i < aCount; i++) {
            int nameIdx = in.readUnsignedShort();
            int len = in.readInt();
            byte[] info = new byte[len];
            in.readFully(info);
            String name = (nameIdx > 0 && nameIdx < utf8.length) ? utf8[nameIdx] : "";
            if ("MethodParameters".equals(name) || "EnclosingMethod".equals(name) || "InnerClasses".equals(name)) {
                continue;
            }
            kept++;
            attrOut.writeShort(nameIdx);
            attrOut.writeInt(len);
            attrOut.write(info);
        }
        out.writeShort(kept);
        out.write(attrBos.toByteArray());
    }
}
