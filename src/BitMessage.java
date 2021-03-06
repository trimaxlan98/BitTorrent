/* BitMessage.java: para construir y analizar mensajes BitTorrent   */

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BitMessage {
    private static final int INTEGER_LENGTH = 4;
    /* Unpacked Message: información sobre el contenido de la carga útil del mensaje*/
    private MessageType type;      // tipo de BitMessage, cf. protocolo de cliente
    private int blockLength = -1;  // longitud de un bloque solicitado
    private int index = -1;        // índice de pieza que contiene el bloque solicitado
    private int begin = -1;        // desplazamiento dentro de una pieza de un bloque solicitado
    private byte[] block = null;   // bloque de datos en sí, subconjunto contiguo de una pieza
    private byte[] bitfield = null;// para mensaje de campo de bits

    /* MessageType: todos los tipos de mensajes posibles en el protocolo del cliente */
    public enum MessageType {
        KEEP_ALIVE,
        CHOKE,
        UNCHOKE,
        INTERESTED,
        UNINTERESTED,
        HAVE,
        BITFIELD,
        REQUEST,
        PIECE,
        CANCEL
    }

    /* BitMessage(MessageType):constructor para mensajes sin carga útil  */
    /* MessageType: KEEP_ALIVE, CHOKE, UNCHOKE, INTERESTED, UNINTERESTED */
    public BitMessage(MessageType type) {
        this.type = type;
    }

    /* BitMessage(MessageType, int): contructor para un mensaje HAVE*/
    public BitMessage(MessageType type, int index) {
        this.type = type;
        this.index = index;
    }

    /* BitMessage(MessageType, byte[]): constructor para un mensaje BITFIELD  */
    public BitMessage(MessageType type, byte[] bitfield) {
        this.type = type;
        this.bitfield = bitfield;
    }

    /* BitMessage(MessageType, int, int, int): bloquear mensajes de transferencia */
    /* MessageType: REQUEST o CANCEL */
    public BitMessage(MessageType type, int index, int begin, int blockLength) {
        this.type = type;
        this.index = index;
        this.begin = begin;
        this.blockLength = blockLength;
    }

    /* BitMessage(MessageType, int, int, byte[]): bloquear mensajes de transferencia */
    /* MessageType: PIECE */
    public BitMessage(MessageType type, int index, int begin, byte[] block) {
        this.type = type;
        this.index = index;
        this.begin = begin;
        this.block = block;
    }

    public MessageType getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public int getBegin() {
        return begin;
    }

    public int getBlockLength() {
        return blockLength;
    }

    public byte[] getBlock() {
        if (type != MessageType.PIECE) {
            throw new RuntimeException("error: getBlock called on non-PIECE");
        }
        return block;
    }

    public byte[] getBitfield() {
        if (type != MessageType.BITFIELD) {
            throw new RuntimeException("error: getBitfield called on non-BITFIELD");
        }
        return bitfield;
    }

    /* pack:empaqueta un mensaje en un byte [] para enviarlo a través de la red */
    public byte[] pack() {
        ByteBuffer buf = null;

        if (type == MessageType.KEEP_ALIVE) {                // {0000}
            buf = ByteBuffer.allocate(INTEGER_LENGTH);
            buf.putInt(0);
        } else if (type == MessageType.CHOKE) {              // {0001, 0}
            buf = ByteBuffer.allocate(INTEGER_LENGTH + 1);   
            buf.putInt(1);                                   
            buf.put("0".getBytes(StandardCharsets.US_ASCII));
        } else if (type == MessageType.UNCHOKE) {            // {0001, 1}
            buf = ByteBuffer.allocate(INTEGER_LENGTH + 1);
            buf.putInt(1);
            buf.put("1".getBytes(StandardCharsets.US_ASCII));
        } else if (type == MessageType.INTERESTED) {
            buf = ByteBuffer.allocate(INTEGER_LENGTH + 1);
            buf.putInt(1);
            buf.put("2".getBytes(StandardCharsets.US_ASCII));
        } else if (type == MessageType.UNINTERESTED) {
            buf = ByteBuffer.allocate(INTEGER_LENGTH + 1);
            buf.putInt(1);
            buf.put("3".getBytes(StandardCharsets.US_ASCII));
        } else if (type == MessageType.HAVE) {
            buf = ByteBuffer.allocate(2 * INTEGER_LENGTH + 1);
            buf.putInt(INTEGER_LENGTH + 1);
            buf.put("4".getBytes(StandardCharsets.US_ASCII));
            buf.putInt(index);
        } else if (type == MessageType.BITFIELD) {
            if (bitfield == null) {
                throw new RuntimeException("Uninitialized variables for bitfield");
            }
            buf = ByteBuffer.allocate(INTEGER_LENGTH + 1 + bitfield.length);
            buf.putInt(1 + bitfield.length);
            buf.put("5".getBytes(StandardCharsets.US_ASCII));
            buf.put(bitfield, 0, bitfield.length);
        } else if (type == MessageType.REQUEST) {
            if (index == -1 || begin == -1 || blockLength == -1) {
                throw new RuntimeException("Uninitialized variables for request");
            }
            buf = ByteBuffer.allocate(4 * INTEGER_LENGTH + 1);
            buf.putInt(3 * INTEGER_LENGTH + 1);
            buf.put("6".getBytes(StandardCharsets.US_ASCII));
            buf.putInt(index);
            buf.putInt(begin);
            buf.putInt(blockLength);
        } else if (type == MessageType.PIECE) {
            if (index == -1 || begin == -1 || block == null) {
                throw new RuntimeException("Uninitialized variables for piece");
            }
            buf = ByteBuffer.allocate(3 * INTEGER_LENGTH + 1 + block.length);
            buf.putInt(2 * INTEGER_LENGTH + 1 + block.length);
            buf.put("7".getBytes(StandardCharsets.US_ASCII));
            buf.putInt(index);
            buf.putInt(begin);
            buf.put(block, 0, block.length);
        } else if (type == MessageType.CANCEL) {
            if (index == -1 || begin == -1 || blockLength == -1) {
                throw new RuntimeException("Uninitialized variables for cancel");
            }
            buf = ByteBuffer.allocate(4 * INTEGER_LENGTH + 1);
            buf.putInt(3 * INTEGER_LENGTH + 1);
            buf.put("8".getBytes(StandardCharsets.US_ASCII));
            buf.putInt(index);
            buf.putInt(begin);
            buf.putInt(blockLength);
        // NOTA:El protocolo completo de BitTorrent tiene otro tipo de mensaje PORT
        } else {
            throw new RuntimeException("Unrecognized BitMessage type: " + type);
        }

        if (buf != null) {
            return buf.array();
        }
        return null;
    }

    /* unpack: convierte el byte recibido [] en el BitMessage correspondiente*/
    public static BitMessage unpack(byte[] message) {
        ByteBuffer buf = ByteBuffer.wrap(message);
        int len = buf.getInt();

        // manejar el mensaje KEEP_ALIVE
        if (len == 0) {
            return new BitMessage(MessageType.KEEP_ALIVE);
        }

        byte[] t = new byte[1];
        buf.get(t, 0, 1);
        String typeStr = new String(t, StandardCharsets.US_ASCII);

        // Manejar mensajes de estatus  (CHOKE, UNCHOKE, INTERESTED, UNINTERESTED)
        if (len == 1) {
            if (typeStr.equals("0")) {
                return new BitMessage(MessageType.CHOKE);
            } else if (typeStr.equals("1")) {
                return new BitMessage(MessageType.UNCHOKE);
            } else if (typeStr.equals("2")) {
                return new BitMessage(MessageType.INTERESTED);
            } else if (typeStr.equals("3")) {
                return new BitMessage(MessageType.UNINTERESTED);
            } else {
                throw new RuntimeException("Unpack found unrecognized len=1 msg");
            }
        }

        // Manejo mensajes HAVE, BITFIELD, REQUEST, CANCEL, PIECE
        if (typeStr.equals("4")) {
            int pieceIndex = buf.getInt();
            return new BitMessage(MessageType.HAVE, pieceIndex);
        } else if (typeStr.equals("5")) {
            byte[] bitMap = new byte[len - 1];
            buf.get(bitMap, 0, len - 1);
            return new BitMessage(MessageType.BITFIELD, bitMap);
        } else if (typeStr.equals("6")) {
            int i = buf.getInt();    // index
            int b = buf.getInt();    // begin
            int l = buf.getInt();    // length
            return new BitMessage(MessageType.REQUEST, i, b, l);
        } else if (typeStr.equals("7")) {
            int i = buf.getInt();    // index
            int b = buf.getInt();    // begin
            byte[] blockData = new byte[len - 9];
            buf.get(blockData, 0, len - 9);
            return new BitMessage(MessageType.PIECE, i, b, blockData);
        } else if (typeStr.equals("8")) {
            int i = buf.getInt();    // index
            int b = buf.getInt();    // begin
            int l = buf.getInt();    // length
            return new BitMessage(MessageType.CANCEL, i, b, l);
        } else {
            throw new RuntimeException("Unpack found unrecognized MessageType");
        }
    }
}
