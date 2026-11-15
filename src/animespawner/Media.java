package animespawner;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import org.w3c.dom.*;

final class Media {
    static final long MAX_PIXELS=48_000_000L; // Maximum decoded pixels across all frames (~192 MB).
    record Clip(List<BufferedImage> frames,List<Integer> delays) {
        Clip { if(frames.isEmpty()||frames.size()!=delays.size())throw new IllegalArgumentException("Empty or invalid animation"); frames=List.copyOf(frames);delays=List.copyOf(delays); }
        int width(){return frames.get(0).getWidth();} int height(){return frames.get(0).getHeight();}
        long duration(){return delays.stream().mapToLong(Integer::longValue).sum();}
        int index(double millis){long t=(long)Math.max(0,millis)%duration();for(int i=0;i<delays.size();i++){t-=delays.get(i);if(t<0)return i;}return 0;}
        BufferedImage at(double millis){return frames.get(index(millis));}
    }
    static Clip load(Path path) throws IOException {
        if(Files.size(path)>100_000_000)throw new IOException("File exceeds the 100 MB import limit.");
        Clip apng=Apng.tryLoad(path);if(apng!=null)return apng;
        try(ImageInputStream in=ImageIO.createImageInputStream(path.toFile())){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext())throw new IOException("Unsupported media. Import PNG, GIF, JPEG or BMP, or convert the file in Creators.");
            ImageReader r=readers.next();
            try{
                r.setInput(in);String format=r.getFormatName();
                if(!format.equalsIgnoreCase("gif")){
                    check(r.getWidth(0),r.getHeight(0),1);
                    return new Clip(List.of(argb(r.read(0))),List.of(100));
                }
                int n=r.getNumImages(true);if(n>600)throw new IOException("Animation exceeds 600 frames. Use a shorter clip.");
                int w=r.getWidth(0),h=r.getHeight(0);
                IIOMetadata stream=r.getStreamMetadata();
                if(stream!=null){Node d=find(stream.getAsTree("javax_imageio_gif_stream_1.0"),"LogicalScreenDescriptor");if(d!=null){w=attrInt(d,"logicalScreenWidth",w);h=attrInt(d,"logicalScreenHeight",h);}}
                check(w,h,n);var frames=new ArrayList<BufferedImage>();var delays=new ArrayList<Integer>();
                BufferedImage canvas=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                for(int i=0;i<n;i++){
                    Node root=r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    Node desc=find(root,"ImageDescriptor"),control=find(root,"GraphicControlExtension");
                    int x=attrInt(desc,"imageLeftPosition",0),y=attrInt(desc,"imageTopPosition",0);
                    String disposal=attr(control,"disposalMethod","none");
                    BufferedImage prior=disposal.equals("restoreToPrevious")?argb(canvas):null;
                    BufferedImage frame=r.read(i);Graphics2D g=canvas.createGraphics();g.drawImage(frame,x,y,null);g.dispose();
                    frames.add(argb(canvas));delays.add(Math.max(20,attrInt(control,"delayTime",10)*10));
                    if(disposal.equals("restoreToBackgroundColor")){g=canvas.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(x,y,frame.getWidth(),frame.getHeight());g.dispose();}
                    else if(prior!=null)canvas=prior;
                }
                return new Clip(frames,delays);
            }finally{r.dispose();}
        }
    }
    static Clip sheet(BufferedImage source,int cols,int rows,int count,int fps) throws IOException{
        if(cols<1||rows<1||count<1||count>cols*rows||cols>source.getWidth()||rows>source.getHeight()||fps<1||fps>60)throw new IOException("Invalid sprite grid or frame rate.");
        if(source.getWidth()%cols!=0||source.getHeight()%rows!=0)throw new IOException("Image dimensions must divide evenly into the grid.");
        int w=source.getWidth()/cols,h=source.getHeight()/rows;check(w,h,count);
        var frames=new ArrayList<BufferedImage>();for(int i=0;i<count;i++)frames.add(argb(source.getSubimage((i%cols)*w,(i/cols)*h,w,h)));
        return new Clip(frames,Collections.nCopies(count,Math.max(20,1000/fps)));
    }
    static Clip key(Clip src,Color key,int tolerance){
        var frames=new ArrayList<BufferedImage>();int threshold=tolerance*tolerance*3;
        for(BufferedImage original:src.frames()){
            BufferedImage b=argb(original);
            for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
                int p=b.getRGB(x,y),dr=((p>>16)&255)-key.getRed(),dg=((p>>8)&255)-key.getGreen(),db=(p&255)-key.getBlue();
                if(dr*dr+dg*dg+db*db<=threshold)b.setRGB(x,y,0);
            }frames.add(b);
        }return new Clip(frames,src.delays());
    }
    static void writeGif(Clip clip,Path path)throws IOException{
        ImageWriter w=ImageIO.getImageWritersByFormatName("gif").next();
        try(ImageOutputStream out=ImageIO.createImageOutputStream(path.toFile())){
            w.setOutput(out);w.prepareWriteSequence(null);
            for(int i=0;i<clip.frames().size();i++){
                BufferedImage im=clip.frames().get(i);ImageWriteParam p=w.getDefaultWriteParam();
                IIOMetadata m=w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(im),p);
                IIOMetadataNode root=(IIOMetadataNode)m.getAsTree("javax_imageio_gif_image_1.0");
                IIOMetadataNode control=(IIOMetadataNode)find(root,"GraphicControlExtension");
                control.setAttribute("disposalMethod","restoreToBackgroundColor");control.setAttribute("delayTime",String.valueOf(Math.max(2,clip.delays().get(i)/10)));
                if(i==0){IIOMetadataNode ext=new IIOMetadataNode("ApplicationExtensions"),app=new IIOMetadataNode("ApplicationExtension");app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});ext.appendChild(app);root.appendChild(ext);}
                m.setFromTree("javax_imageio_gif_image_1.0",root);w.writeToSequence(new IIOImage(im,null,m),p);
            }w.endWriteSequence();
        }finally{w.dispose();}
    }
    static BufferedImage argb(BufferedImage src){BufferedImage b=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=b.createGraphics();g.drawImage(src,0,0,null);g.dispose();return b;}
    static void check(int w,int h,int n)throws IOException{if(w<1||h<1||w>8192||h>8192||n<1||n>600||(long)w*h*n>MAX_PIXELS)throw new IOException("Media is too large. Use fewer frames or smaller dimensions (192 MB decoded limit).");}
    static Node find(Node root,String name){if(root==null)return null;if(root.getNodeName().equals(name))return root;for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling()){Node found=find(n,name);if(found!=null)return found;}return null;}
    static String attr(Node n,String key,String fallback){if(n==null)return fallback;Node a=n.getAttributes().getNamedItem(key);return a==null?fallback:a.getNodeValue();}
    static int attrInt(Node n,String key,int fallback){return Integer.parseInt(attr(n,key,""+fallback));}
}
