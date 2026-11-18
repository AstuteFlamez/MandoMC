package net.mandomc.gameplay.jawa;

/** Gray shops quote a single item; former bundle prices are divided in integer cents. */
public final class JawaPurchase {
    private JawaPurchase() { }
    public static long unitPrice(JawaSettings.Offer offer) {
        return Math.addExact(offer.price(), offer.amount() - 1) / offer.amount();
    }
    public static long price(JawaSettings.Offer offer, int amount) {
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Invalid purchase amount");
        return Math.multiplyExact(unitPrice(offer), amount);
    }
}
