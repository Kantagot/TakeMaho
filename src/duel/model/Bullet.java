package duel.model;

public class Bullet {
    public double x, y, vx, vy;
    public int ownerId;     // เปลี่ยนเจ้าของได้ตอนสะท้อน
    public boolean homing;
    public boolean dead = false;

    public Bullet(double x, double y, double vx, double vy, int ownerId, boolean homing) {
        this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.ownerId=ownerId; this.homing=homing;
    }
}
