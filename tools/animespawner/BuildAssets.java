package animespawner;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
public final class BuildAssets {
    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(Artwork.icon(),"png",png);byte[] bytes=png.toByteArray();
        ByteBuffer ico=ByteBuffer.allocate(22+bytes.length).order(ByteOrder.LITTLE_ENDIAN);ico.putShort((short)0).putShort((short)1).putShort((short)1).put((byte)64).put((byte)64).put((byte)0).put((byte)0).putShort((short)1).putShort((short)32).putInt(bytes.length).putInt(22).put(bytes);Files.write(Path.of(args[0]),ico.array());
    }
}
