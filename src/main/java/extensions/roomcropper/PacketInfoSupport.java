package extensions.roomcropper;

import gearth.extensions.IExtension;
import gearth.extensions.ExtensionBase.MessageListener;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import gearth.protocol.HMessage.Direction;
import gearth.services.packet_info.PacketInfo;
import gearth.services.packet_info.PacketInfoManager;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PacketInfoSupport {
    private final Object lock = new Object();
    private PacketInfoManager packetInfoManager = new PacketInfoManager(new ArrayList());
    private Map<String, List<MessageListener>> incomingMessageListeners = new HashMap();
    private Map<String, List<MessageListener>> outgoingMessageListeners = new HashMap();
    private IExtension extension;

    public PacketInfoSupport(IExtension extension) {
        this.extension = extension;
        extension.onConnect((host, port, hotelversion, clientIdentifier, clientType, packetInfoManager) -> {
            this.packetInfoManager = packetInfoManager;
        });
        extension.intercept(Direction.TOSERVER, (message) -> {
            this.onReceivePacket(Direction.TOSERVER, message);
        });
        extension.intercept(Direction.TOCLIENT, (message) -> {
            this.onReceivePacket(Direction.TOCLIENT, message);
        });
    }

    private void onReceivePacket(Direction direction, HMessage message) {
        Set<MessageListener> callbacks = new HashSet();
        Map<String, List<MessageListener>> messageListeners = direction == Direction.TOSERVER ? this.outgoingMessageListeners : this.incomingMessageListeners;
        List<PacketInfo> packetInfos = this.packetInfoManager.getAllPacketInfoFromHeaderId(direction, message.getPacket().headerId());
        Iterator var6 = packetInfos.iterator();

        while(var6.hasNext()) {
            PacketInfo packetInfo = (PacketInfo)var6.next();
            List<MessageListener> listeners_hash = (List)messageListeners.get(packetInfo.getHash());
            List<MessageListener> listeners_name = (List)messageListeners.get(packetInfo.getName());
            if (listeners_hash != null) {
                callbacks.addAll(listeners_hash);
            }

            if (listeners_name != null) {
                callbacks.addAll(listeners_name);
            }
        }

        var6 = callbacks.iterator();

        while(var6.hasNext()) {
            MessageListener listener = (MessageListener)var6.next();
            listener.act(message);
            message.getPacket().resetReadIndex();
        }

    }

    public void intercept(Direction direction, String hashOrName, MessageListener messageListener) {
        Map<String, List<MessageListener>> messageListeners = direction == Direction.TOSERVER ? this.outgoingMessageListeners : this.incomingMessageListeners;
        messageListeners.computeIfAbsent(hashOrName, (k) -> {
            return new ArrayList();
        });
        ((List)messageListeners.get(hashOrName)).add(messageListener);
    }

    private boolean send(Direction direction, String hashOrName, Object... objects) {
        PacketInfo fromname = this.packetInfoManager.getPacketInfoFromName(direction, hashOrName);
        int headerId;
        if (fromname != null) {
            headerId = fromname.getHeaderId();
        } else {
            PacketInfo fromHash = this.packetInfoManager.getPacketInfoFromHash(direction, hashOrName);
            if (fromHash == null) {
                return false;
            }

            headerId = fromHash.getHeaderId();
        }

        try {
            HPacket packetToSend = new HPacket(headerId, objects);
            return direction == Direction.TOCLIENT ? this.extension.sendToClient(packetToSend) : this.extension.sendToServer(packetToSend);
        } catch (InvalidParameterException var7) {
            return false;
        }
    }

    public boolean sendToClient(String hashOrName, Object... objects) {
        return this.send(Direction.TOCLIENT, hashOrName, objects);
    }

    public boolean sendToServer(String hashOrName, Object... objects) {
        return this.send(Direction.TOSERVER, hashOrName, objects);
    }

    public PacketInfoManager getPacketInfoManager() {
        return this.packetInfoManager;
    }
}
