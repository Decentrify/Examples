package se.sics.ktoolbox.examples.channel;

import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Kompics;
import se.sics.ktoolbox.examples.channel.AComp.AInit;
import se.sics.ktoolbox.examples.channel.BComp.BInit;

/**
 * Hello world!
 *
 */
public class Launcher extends ComponentDefinition {

    public Launcher() {
        Component a1 = create(AComp.class, new AInit(0));
        Component a2 = create(AComp.class, new AInit(2));
        Component b1 = create(BComp.class, new BInit(1));
        Component b2 = create(BComp.class, new BInit(3));

        ComponentHelper.connect(a1.getPositive(XYPort.class), new IdExtractor(0, true, XEvent.class),
                b1.getNegative(XYPort.class), new IdExtractor(7, false, YEvent.class));
        ComponentHelper.connect(a1.getPositive(XYPort.class), new IdExtractor(4, true, XEvent.class),
                b2.getNegative(XYPort.class), new IdExtractor(3, false, YEvent.class));
        ComponentHelper.connect(a2.getPositive(XYPort.class), new IdExtractor(6, false, XEvent.class),
                b1.getNegative(XYPort.class), new IdExtractor(1, true, YEvent.class));
        ComponentHelper.connect(a2.getPositive(XYPort.class), new IdExtractor(2, false, XEvent.class),
                b2.getNegative(XYPort.class), new IdExtractor(5, true, YEvent.class));
    }

    public static void main(String[] args) {
        start();
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public static void start() {
        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(Launcher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
    }

    public static void stop() {
        Kompics.shutdown();
    }
}
