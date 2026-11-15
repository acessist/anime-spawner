package animespawner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

final class DesktopSprite extends JWindow {
    final AnimeSpawner app;final Library.Asset asset;final Media.Clip clip;
    boolean paused,locked;double time;Point grab;final JPanel canvas;
    DesktopSprite(AnimeSpawner app,Library.Asset asset,Media.Clip clip){
        super();this.app=app;this.asset=asset;this.clip=clip;
        setName("Anime Spawner desktop character");setType(Window.Type.UTILITY);setFocusableWindowState(false);setAlwaysOnTop(app.alwaysOnTop);
        setBackground(new Color(0,0,0,0));
        canvas=new JPanel(){@Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Artwork.quality(g);g.setComposite(AlphaComposite.Src);g.setColor(new Color(0,0,0,0));g.fillRect(0,0,getWidth(),getHeight());g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,(float)asset.opacity));BufferedImage frame=clip.at(time);if(asset.flip)g.drawImage(frame,getWidth(),0,-getWidth(),getHeight(),null);else g.drawImage(frame,0,0,getWidth(),getHeight(),null);g.dispose();}};
        canvas.setOpaque(false);setContentPane(canvas);resizeSprite();
        MouseAdapter mouse=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){if(e.isPopupTrigger()||SwingUtilities.isRightMouseButton(e))menu(e);else {grab=e.getPoint();app.select(asset);}}
            @Override public void mouseReleased(MouseEvent e){grab=null;}
            @Override public void mouseDragged(MouseEvent e){if(!locked&&grab!=null)setLocation(e.getXOnScreen()-grab.x,e.getYOnScreen()-grab.y);}
            @Override public void mouseWheelMoved(MouseWheelEvent e){asset.size=Math.clamp(asset.size-e.getWheelRotation()*15,64,1000);app.applySettings();}
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e))paused=!paused;}
        };canvas.addMouseListener(mouse);canvas.addMouseMotionListener(mouse);canvas.addMouseWheelListener(mouse);
    }
    void resizeSprite(){int h=asset.size,w=Math.max(24,(int)Math.round(h*(double)clip.width()/clip.height()));setSize(w,h);canvas.repaint();}
    void advance(double delta){if(!paused&&!app.paused)time+=delta*asset.speed;if(isVisible()&&!paused&&!app.paused)canvas.repaint();}
    void menu(MouseEvent e){
        JPopupMenu menu=new JPopupMenu();add(menu,"Open Anime Spawner",app::showManager);add(menu,paused?"Play":"Pause",()->paused=!paused);add(menu,"Flip horizontally",()->{asset.flip=!asset.flip;app.applySettings();});add(menu,locked?"Unlock dragging":"Lock dragging",()->locked=!locked);add(menu,"Duplicate",()->app.spawn(asset));menu.addSeparator();add(menu,"Remove from desktop",()->app.removeSprite(this));menu.show(canvas,e.getX(),e.getY());
    }
    static void add(JPopupMenu m,String text,Runnable r){JMenuItem item=new JMenuItem(text);item.addActionListener(e->r.run());m.add(item);}
    void center(){Rectangle r=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();setLocation(r.x+(r.width-getWidth())/2,r.y+(r.height-getHeight())/2);}
    void keepOnScreen(){Rectangle b=getBounds();boolean visible=false;for(GraphicsDevice d:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())if(d.getDefaultConfiguration().getBounds().intersects(b)){visible=true;break;}if(!visible)center();}
}
