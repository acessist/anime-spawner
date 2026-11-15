package animespawner;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class Tests {
    static int checks;
    static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static BufferedImage solid(int w,int h,Color c){BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=b.createGraphics();g.setColor(c);g.fillRect(0,0,w,h);g.dispose();return b;}
    static void invalid(AnimeSpawner.Task<?> task,String why)throws Exception{try{task.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Path temp=Path.of(args.length>0?args[0]:"work/tests").toAbsolutePath();Files.createDirectories(temp);temp=Files.createTempDirectory(temp,"run-");final Path testDir=temp;
        Media.Clip clip=new Media.Clip(List.of(solid(20,20,Color.RED),solid(20,20,Color.BLUE)),List.of(70,130));
        check(clip.index(69)==0&&clip.index(70)==1&&clip.index(200)==0,"Frame timing and loop boundary");
        Path gif=temp.resolve("timing.gif");Media.writeGif(clip,gif);Media.Clip decoded=Media.load(gif);check(decoded.frames().size()==2,"GIF frame count");check(decoded.delays().equals(List.of(70,130)),"GIF delays round trip");check(decoded.frames().get(1).getRGB(4,4)==Color.BLUE.getRGB(),"GIF second frame pixels");
        BufferedImage transparent=solid(20,20,new Color(0,0,0,0));transparent.setRGB(1,1,Color.GREEN.getRGB());Media.writeGif(new Media.Clip(List.of(transparent,transparent),List.of(100,100)),temp.resolve("transparent.gif"));Media.Clip tr=Media.load(temp.resolve("transparent.gif"));check((tr.frames().get(0).getRGB(10,10)>>>24)==0,"GIF transparency survives export");
        Media.Clip keyed=Media.key(clip,Color.RED,0);check((keyed.frames().get(0).getRGB(2,2)>>>24)==0,"Color key removes selected color");check(keyed.frames().get(1).getRGB(2,2)==Color.BLUE.getRGB(),"Color key preserves other colors");check(clip.frames().get(0).getRGB(2,2)==Color.RED.getRGB(),"Color key does not mutate source");
        BufferedImage sheet=solid(40,20,Color.RED);Graphics2D g=sheet.createGraphics();g.setColor(Color.BLUE);g.fillRect(20,0,20,20);g.dispose();Media.Clip sliced=Media.sheet(sheet,2,1,2,10);check(sliced.frames().get(1).getRGB(2,2)==Color.BLUE.getRGB(),"Sprite row-major ordering");invalid(()->Media.sheet(sheet,3,1,2,10),"Reject uneven sprite dimensions");invalid(()->{Media.check(10000,100,1);return null;},"Bound decoded dimensions");
        Path apng=temp.resolve("composition.apng");writeApng(apng);Media.Clip animated=Media.load(apng);check(animated.frames().size()==3,"APNG all frames decoded");check(animated.delays().equals(List.of(100,200,300)),"APNG fractional delays");check(animated.frames().get(1).getRGB(0,0)==Color.BLUE.getRGB(),"APNG subframe placement");check(animated.frames().get(1).getRGB(3,3)==Color.RED.getRGB(),"APNG preserves outside patch");check(animated.frames().get(2).getRGB(0,0)==Color.RED.getRGB(),"APNG restore-previous disposal");check(animated.frames().get(2).getRGB(3,3)==Color.GREEN.getRGB(),"APNG final patch placement");
        byte[] corrupt=Files.readAllBytes(apng);corrupt[20]^=1;Path broken=temp.resolve("bad.apng");Files.write(broken,corrupt);invalid(()->Media.load(broken),"Reject corrupt PNG CRC");
        Path data=temp.resolve("data");Library lib=new Library(data);check(lib.assets.isEmpty(),"Fresh library contains no bundled animations");Library.Asset a=lib.add(gif,decoded);a.size=345;a.favorite=true;a.flip=true;lib.save();Library again=new Library(data);Library.Asset persisted=again.find(a.id);check(persisted!=null&&persisted.size==345&&persisted.favorite&&persisted.flip,"Library state round trip");check(again.safeAssetPath("assets/test.png")&&!again.safeAssetPath("../outside.png")&&!again.safeAssetPath("assets/../../outside.png"),"Library path containment");
        Properties values=new Properties();values.setProperty("nan","NaN");check(Library.decimal(values,"nan",1,.1,4)==1,"Nonfinite settings rejected");

        Path original=gif;Path owned=again.data.resolve(persisted.file);
        Path audio=again.data.resolve("assets/test.wav");Files.write(audio,new byte[]{1,2,3});persisted.sound="assets/test.wav";again.save();
        again.delete(persisted);
        check(again.assets.isEmpty(),"Delete last library item");
        check(!Files.exists(owned)&&!Files.exists(audio),"Delete copied media and assigned sound");
        check(Files.exists(original),"Delete preserves the original imported file");
        check(new Library(data).assets.isEmpty(),"Deleted animation stays deleted after restart");
        Library.Asset missing=again.add(gif,decoded);Files.delete(again.data.resolve(missing.file));again.delete(missing);
        check(again.assets.isEmpty(),"Missing imported copy can still be removed");
        Library.Asset first=again.add(gif,decoded),second=again.add(gif,decoded);
        Path shared=again.data.resolve("assets/shared.wav");Files.write(shared,new byte[]{1,2,3});first.sound=second.sound="assets/shared.wav";again.save();
        again.delete(first);check(Files.exists(shared),"Deleting one item preserves a shared sound");
        again.delete(second);check(!Files.exists(shared),"Deleting last sound reference removes the copy");
        Library.Asset rollback=again.add(gif,decoded);Path saved=data.resolve("library.properties"),backup=data.resolve("library.backup");
        Files.move(saved,backup);Files.createDirectory(saved);Files.writeString(saved.resolve("blocker"),"test");
        invalid(()->{again.delete(rollback);return null;},"Save failure reports deletion error");
        check(again.find(rollback.id)!=null&&Files.exists(data.resolve(rollback.file)),"Failed deletion restores media and library entry");
        Files.delete(saved.resolve("blocker"));Files.delete(saved);Files.move(backup,saved);
        Properties legacy=new Properties();legacy.setProperty("asset.count","2");legacy.setProperty("asset.0.id","starter-0");legacy.setProperty("asset.0.file","");legacy.setProperty("asset.1.id",rollback.id);legacy.setProperty("asset.1.file",rollback.file);legacy.setProperty("asset.1.name","Imported GIF");Library.write(legacy,saved);
        Library migrated=new Library(data);check(migrated.assets.size()==1&&migrated.find(rollback.id)!=null,"Upgrade ignores old importeds and preserves imports");
        if(args.length>1&&args[1].equals("--ui")){
            final AnimeSpawner[] ref=new AnimeSpawner[1];SwingUtilities.invokeAndWait(()->{try{ref[0]=new AnimeSpawner(testDir.resolve("ui-data"));ref[0].frame.setVisible(true);}catch(IOException ex){throw new RuntimeException(ex);}});
            AnimeSpawner app=ref[0];check(app.library.assets.isEmpty()&&app.selected==null,"Empty app launches without a selected animation");Library.Asset imported=app.library.add(gif,decoded);
            SwingUtilities.invokeAndWait(()->{try{app.select(imported);app.spawn(imported);check(app.sprites.size()==1,"Native transparent overlay spawned");DesktopSprite s=app.sprites.get(0);check(s.isVisible()&&s.getBackground().getAlpha()==0,"Overlay visible and transparent");app.toggleHidden();check(!s.isVisible(),"Hide desktop overlay");app.toggleHidden();app.togglePause();double time=s.time;s.advance(100);check(s.time==time,"Global pause stops frame time");app.togglePause();s.advance(100);check(s.time>time,"Resume advances animation");imported.size=320;imported.opacity=.65;imported.flip=true;s.setLocation(123,234);s.locked=true;app.applySettings();check(s.getHeight()==320,"Live size control");Path scene=testDir.resolve("test.scene");app.saveScene(scene);var items=app.readScene(scene);check(items.size()==1&&items.get(0).x()==123&&items.get(0).y()==234&&items.get(0).opacity()==.65&&items.get(0).flip()&&items.get(0).locked(),"Scene positions and settings round trip");imported.opacity=1;imported.flip=false;app.applySettings();app.deleteAsset(imported);check(app.selected==null&&app.previewClip==null&&app.library.assets.isEmpty()&&app.sprites.isEmpty(),"Deleting active final GIF clears overlays and selection");check(Files.exists(gif),"UI deletion preserves original GIF");app.frame.validate();BufferedImage shot=new BufferedImage(app.frame.getContentPane().getWidth(),app.frame.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=shot.createGraphics();app.frame.getContentPane().printAll(graphics);graphics.dispose();ImageIO.write(shot,"png",testDir.resolve("ui-preview.png").toFile());app.clearDesktop();check(app.sprites.isEmpty(),"Clear desktop disposes instances");app.timer.stop();if(app.tray!=null)SystemTray.getSystemTray().remove(app.tray);app.frame.dispose();}catch(Exception ex){throw new RuntimeException(ex);}});
        }
        System.out.println("PASS: "+checks+" checks");System.out.println("Test output: "+testDir);System.exit(0);
    }
    static void writeApng(Path path)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(Apng.SIGNATURE);byte[] header=ByteBuffer.allocate(13).putInt(4).putInt(4).put((byte)8).put((byte)6).put((byte)0).put((byte)0).put((byte)0).array();Apng.writeChunk(out,"IHDR",header);Apng.writeChunk(out,"acTL",ByteBuffer.allocate(8).putInt(3).putInt(0).array());
        frameControl(out,0,4,4,0,0,1,10,0,0);Apng.writeChunk(out,"IDAT",pngData(solid(4,4,Color.RED)));
        frameControl(out,1,2,2,0,0,2,10,2,0);byte[] blue=pngData(solid(2,2,Color.BLUE));Apng.writeChunk(out,"fdAT",ByteBuffer.allocate(blue.length+4).putInt(2).put(blue).array());
        frameControl(out,3,2,2,2,2,3,10,0,1);byte[] green=pngData(solid(2,2,Color.GREEN));Apng.writeChunk(out,"fdAT",ByteBuffer.allocate(green.length+4).putInt(4).put(green).array());Apng.writeChunk(out,"IEND",new byte[0]);Files.write(path,out.toByteArray());
    }
    static void frameControl(OutputStream out,int seq,int w,int h,int x,int y,int num,int den,int dispose,int blend)throws Exception{Apng.writeChunk(out,"fcTL",ByteBuffer.allocate(26).putInt(seq).putInt(w).putInt(h).putInt(x).putInt(y).putShort((short)num).putShort((short)den).put((byte)dispose).put((byte)blend).array());}
    static byte[] pngData(BufferedImage image)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);ByteArrayOutputStream data=new ByteArrayOutputStream();for(Apng.Chunk c:Apng.chunks(out.toByteArray()))if(c.type().equals("IDAT"))data.write(c.data());return data.toByteArray();}
}
