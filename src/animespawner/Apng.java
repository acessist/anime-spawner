package animespawner;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** APNG composition per W3C PNG Third Edition, section 11.3.6. */
final class Apng {
    static final byte[] SIGNATURE={(byte)137,80,78,71,13,10,26,10};
    record Chunk(String type,byte[] data){}
    record Control(int width,int height,int x,int y,int delay,int dispose,int blend){}
    static List<Chunk> chunks(byte[] bytes)throws IOException{
        List<Chunk> result=new ArrayList<>();try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(!Arrays.equals(in.readNBytes(8),SIGNATURE))throw new IOException("Invalid PNG signature.");
            while(in.available()>0){int length=in.readInt();if(length<0||length>in.available()-8)throw new IOException("Invalid PNG chunk length.");byte[] type=in.readNBytes(4),data=in.readNBytes(length);long crc=Integer.toUnsignedLong(in.readInt());CRC32 check=new CRC32();check.update(type);check.update(data);if(check.getValue()!=crc)throw new IOException("PNG checksum error.");String name=new String(type,StandardCharsets.US_ASCII);result.add(new Chunk(name,data));if(name.equals("IEND"))break;}
        }return result;
    }
    static Media.Clip tryLoad(Path path)throws IOException{
        try(InputStream in=Files.newInputStream(path)){if(!Arrays.equals(in.readNBytes(8),SIGNATURE))return null;}
        List<Chunk> chunks=chunks(Files.readAllBytes(path));Chunk animation=chunks.stream().filter(c->c.type.equals("acTL")).findFirst().orElse(null);if(animation==null)return null;
        if(animation.data.length!=8)throw new IOException("Invalid APNG animation header.");
        byte[] ihdr=chunks.get(0).data;if(!chunks.get(0).type.equals("IHDR")||ihdr.length!=13)throw new IOException("Missing PNG header.");
        ByteBuffer header=ByteBuffer.wrap(ihdr);int width=header.getInt(),height=header.getInt(),expected=ByteBuffer.wrap(animation.data).getInt();Media.check(width,height,expected);
        BufferedImage canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);List<Chunk> shared=new ArrayList<>();List<BufferedImage> frames=new ArrayList<>();List<Integer> delays=new ArrayList<>();
        Control control=null;ByteArrayOutputStream compressed=new ByteArrayOutputStream();int sequence=0;
        for(Chunk chunk:chunks){
            switch(chunk.type){
                case "PLTE","tRNS","gAMA","sRGB","cHRM" -> shared.add(chunk);
                case "fcTL" -> {
                    if(control!=null){canvas=append(canvas,control,compressed.toByteArray(),ihdr,shared,frames,delays);compressed.reset();}
                    if(chunk.data.length!=26)throw new IOException("Invalid APNG frame header.");ByteBuffer b=ByteBuffer.wrap(chunk.data);if(b.getInt()!=sequence++)throw new IOException("APNG sequence is out of order.");
                    int w=b.getInt(),h=b.getInt(),x=b.getInt(),y=b.getInt(),num=Short.toUnsignedInt(b.getShort()),den=Short.toUnsignedInt(b.getShort()),dispose=Byte.toUnsignedInt(b.get()),blend=Byte.toUnsignedInt(b.get());
                    if(w<1||h<1||x<0||y<0||(long)x+w>width||(long)y+h>height||dispose>2||blend>1)throw new IOException("Invalid APNG frame bounds or operations.");
                    if(frames.size()>=expected)throw new IOException("APNG contains more frames than declared.");
                    control=new Control(w,h,x,y,Math.max(20,(int)(1000L*num/(den==0?100:den))),dispose,blend);
                }
                case "IDAT" -> {if(control!=null)compressed.write(chunk.data);}
                case "fdAT" -> {if(control==null||chunk.data.length<4)throw new IOException("Unexpected APNG frame data.");if(ByteBuffer.wrap(chunk.data).getInt()!=sequence++)throw new IOException("APNG sequence is out of order.");compressed.write(chunk.data,4,chunk.data.length-4);}
                case "IEND" -> {if(control!=null){canvas=append(canvas,control,compressed.toByteArray(),ihdr,shared,frames,delays);control=null;}}
            }
        }
        if(frames.size()!=expected)throw new IOException("Incomplete APNG animation.");return new Media.Clip(frames,delays);
    }
    static BufferedImage append(BufferedImage canvas,Control c,byte[] compressed,byte[] originalHeader,List<Chunk> shared,List<BufferedImage> frames,List<Integer> delays)throws IOException{
        if(compressed.length==0)throw new IOException("Empty APNG frame.");byte[] header=originalHeader.clone();ByteBuffer.wrap(header).putInt(c.width).putInt(c.height);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(SIGNATURE);writeChunk(bytes,"IHDR",header);for(Chunk part:shared)writeChunk(bytes,part.type,part.data);writeChunk(bytes,"IDAT",compressed);writeChunk(bytes,"IEND",new byte[0]);
        BufferedImage patch=ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));if(patch==null)throw new IOException("Could not decode APNG frame.");
        BufferedImage previous=c.dispose==2?Media.argb(canvas):null;Graphics2D g=canvas.createGraphics();if(c.blend==0){g.setComposite(AlphaComposite.Clear);g.fillRect(c.x,c.y,c.width,c.height);}g.setComposite(AlphaComposite.SrcOver);g.drawImage(patch,c.x,c.y,null);g.dispose();frames.add(Media.argb(canvas));delays.add(c.delay);
        if(c.dispose==1){g=canvas.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(c.x,c.y,c.width,c.height);g.dispose();}else if(c.dispose==2)canvas=previous;return canvas;
    }
    static void writeChunk(OutputStream out,String name,byte[] data)throws IOException{DataOutputStream d=new DataOutputStream(out);byte[] type=name.getBytes(StandardCharsets.US_ASCII);d.writeInt(data.length);d.write(type);d.write(data);CRC32 crc=new CRC32();crc.update(type);crc.update(data);d.writeInt((int)crc.getValue());}
}
