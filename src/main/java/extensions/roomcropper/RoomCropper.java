package extensions.roomcropper;

import gearth.extensions.Extension;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.PacketInfoSupport;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

@ExtensionInfo(
        Title = "RoomCropper",
        Description = "Crop floorplans to occupied tiles",
        Version = "0.2",
        Author = "WiredSpast"
)

public class RoomCropper extends Extension {
    private PacketInfoSupport packetInfoSupport;

    private String rawFloorPlan;
    private List<Pair<Integer, Integer>> occupiedTiles = new ArrayList<>();
    private Pair<Integer, Integer> roomEntryTile;

    public RoomCropper(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        new RoomCropper(args).run();
    }

    @Override
    protected void initExtension() {
        packetInfoSupport = new PacketInfoSupport(this);
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "Chat", this::onChatSend);

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "FloorHeightMap", this::onHeightMap);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "RoomOccupiedTiles", this::onOccupiedTiles);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "RoomEntryTile", this::onRoomEntryTile);
    }

    private void onHeightMap(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.readBoolean();
        packet.readInteger();
        rawFloorPlan = packet.readString();
    }

    private void onOccupiedTiles(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        occupiedTiles.clear();
        int n = packet.readInteger();
        for(int i = 0; i < n; i++) {
            int x = packet.readInteger();
            int y = packet.readInteger();
            occupiedTiles.add(Pair.of(x, y));
        }
    }

    private void onRoomEntryTile(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        int x = packet.readInteger();
        int y = packet.readInteger();
        roomEntryTile = Pair.of(x, y);

    }

    private void onChatSend(HMessage hMessage) {
        String msg = hMessage.getPacket().readString();
        if(msg.startsWith(":crop")) {
            hMessage.setBlocked(true);

            new Thread(() -> {
                packetInfoSupport.sendToServer("GetHeightMap");
                sleep(50);
                packetInfoSupport.sendToServer("GetRoomEntryTile");
                sleep(50);
                packetInfoSupport.sendToServer("GetOccupiedTiles");
                sleep(500);

                cropFloorPlan();

                if(msg.contains("export")) {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    StringSelection selection = new StringSelection(rawFloorPlan);
                    clipboard.setContents(selection, selection);
                    packetInfoSupport.sendToServer("Whisper", "x Copied cropped floorplan to clipboard!", 0);
                } else {
                    packetInfoSupport.sendToServer("UpdateFloorProperties", rawFloorPlan, roomEntryTile.getLeft(), roomEntryTile.getRight(), 0, -2, -2);
                }
            }).start();
        }
    }

    private void cropFloorPlan() {
        String[] splitFloorPlan = rawFloorPlan.split("\r");
        for(int i = 0; i < splitFloorPlan.length; i++) {
            char[] row = splitFloorPlan[i].toCharArray();
            for(int j = 0; j < row.length; j++) {
                if(!occupiedTiles.contains(Pair.of(j, i)) && !roomEntryTile.equals(Pair.of(j, i))) {
                    row[j] = 'x';
                }
            }
            splitFloorPlan[i] = new String(row);
        }
        rawFloorPlan = StringUtils.join(splitFloorPlan, "\r");
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
