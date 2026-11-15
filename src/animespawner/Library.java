package animespawner;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class Library {
    static final class Asset {
        String id,name,file,sound="";int size=260;double speed=1,opacity=1;boolean flip=false,favorite=false;Media.Clip clip;
        Asset(String id,String name,String file){this.id=id;this.name=name;this.file=file;}
        synchronized Media.Clip load(Path data)throws IOException{if(clip==null)clip=Media.load(data.resolve(file));return clip;}
        String type(){return file.substring(file.lastIndexOf('.')+1).toUpperCase(Locale.ROOT);}
        @Override public String toString(){return name;}
    }
    final Path data;final List<Asset> assets=new java.util.concurrent.CopyOnWriteArrayList<>();final Properties prefs=new Properties();
    Library(Path data)throws IOException{
        this.data=data;Files.createDirectories(data.resolve("assets"));Files.createDirectories(data.resolve("scenes"));
        Path file=data.resolve("library.properties");if(Files.exists(file)){try(InputStream in=Files.newInputStream(file)){prefs.load(in);}}
        int count=integer(prefs,"asset.count",0,0,1000);
        for(int i=0;i<count;i++){
            String prefix="asset."+i+".",id=prefs.getProperty(prefix+"id","");if(id.isBlank()||id.startsWith("starter-"))continue;
            Asset a=find(id);if(a==null){String f=prefs.getProperty(prefix+"file","");if(f.isBlank()||!safeAssetPath(f))continue;a=new Asset(id,prefs.getProperty(prefix+"name","Animation"),f);assets.add(a);}
            a.name=prefs.getProperty(prefix+"name",a.name);a.size=integer(prefs,prefix+"size",260,64,1000);a.speed=decimal(prefs,prefix+"speed",1,.1,4);a.opacity=decimal(prefs,prefix+"opacity",1,.1,1);a.flip=Boolean.parseBoolean(prefs.getProperty(prefix+"flip"));a.favorite=Boolean.parseBoolean(prefs.getProperty(prefix+"favorite"));
            String sound=prefs.getProperty(prefix+"sound","");if(sound.isEmpty()||safeAssetPath(sound))a.sound=sound;
        }
    }
    boolean safeAssetPath(String f){Path p=Path.of(f);return !p.isAbsolute()&&data.resolve(p).normalize().startsWith(data.resolve("assets").normalize());}
    Asset find(String id){return assets.stream().filter(a->a.id.equals(id)).findFirst().orElse(null);}
    /** Delete only library-owned copies, rolling back moves if saving fails. */
    synchronized void delete(Asset asset) throws IOException {
        int index = assets.indexOf(asset);
        if (index < 0) return;
        Map<Path, Path> staged = new LinkedHashMap<>();
        List<String> owned = new ArrayList<>();
        owned.add(asset.file);
        if (!asset.sound.isEmpty()) owned.add(asset.sound);
        Path stash = Files.createTempDirectory(data, "delete-");
        try {
            for (String file : owned) {
                if (!safeAssetPath(file)) throw new IOException("Invalid library path.");
                boolean shared = assets.stream().anyMatch(other -> other != asset
                    && (file.equals(other.file) || file.equals(other.sound)));
                Path source = data.resolve(file).normalize();
                if (!shared && Files.exists(source) && !staged.containsKey(source)) {
                    Path destination = stash.resolve(Integer.toString(staged.size()));
                    Files.move(source, destination);
                    staged.put(source, destination);
                }
            }
            assets.remove(asset);
            save();
        } catch (IOException failure) {
            if (!assets.contains(asset)) assets.add(Math.min(index, assets.size()), asset);
            for (var entry : staged.entrySet()) {
                try { Files.move(entry.getValue(), entry.getKey()); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            try { Files.deleteIfExists(stash); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        asset.clip = null;
        for (Path path : staged.values()) Files.deleteIfExists(path);
        Files.deleteIfExists(stash);
    }
    Asset add(Path source,Media.Clip clip)throws IOException{
        String id=UUID.randomUUID().toString(),ext=source.getFileName().toString();ext=ext.contains(".")?ext.substring(ext.lastIndexOf('.')).toLowerCase(Locale.ROOT):".png";
        String file="assets/"+id+ext;Files.copy(source,data.resolve(file));
        Asset a=new Asset(id,source.getFileName().toString(),file);a.clip=clip;assets.add(a);save();return a;
    }
    Asset addClip(String name,Media.Clip clip)throws IOException{
        String id=UUID.randomUUID().toString(),file="assets/"+id+".gif";Media.writeGif(clip,data.resolve(file));Asset a=new Asset(id,name,file);a.clip=clip;assets.add(a);save();return a;
    }
    void save()throws IOException{
        Properties p=new Properties();for(String k:prefs.stringPropertyNames())if(!k.startsWith("asset."))p.setProperty(k,prefs.getProperty(k));
        p.setProperty("asset.count",""+assets.size());
        for(int i=0;i<assets.size();i++){
            Asset a=assets.get(i);String k="asset."+i+".";p.setProperty(k+"id",a.id);p.setProperty(k+"name",a.name);p.setProperty(k+"file",a.file);p.setProperty(k+"sound",a.sound);p.setProperty(k+"size",""+a.size);p.setProperty(k+"speed",""+a.speed);p.setProperty(k+"opacity",""+a.opacity);p.setProperty(k+"flip",""+a.flip);p.setProperty(k+"favorite",""+a.favorite);
        }write(p,data.resolve("library.properties"));
    }
    static void write(Properties p,Path file)throws IOException{
        Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"anime-",".tmp");
        try{try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"Anime Spawner");}try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
    static int integer(Properties p,String k,int fallback,int min,int max){try{return Math.clamp(Integer.parseInt(p.getProperty(k)),min,max);}catch(Exception ex){return fallback;}}
    static double decimal(Properties p,String k,double fallback,double min,double max){try{double v=Double.parseDouble(p.getProperty(k));return Double.isFinite(v)?Math.clamp(v,min,max):fallback;}catch(Exception ex){return fallback;}}
}
