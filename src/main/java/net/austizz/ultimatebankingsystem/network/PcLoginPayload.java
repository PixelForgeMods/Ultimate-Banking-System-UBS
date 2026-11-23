package net.austizz.ultimatebankingsystem.network;

import io.netty.buffer.ByteBuf;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class PcLoginPayload {
    private PcLoginPayload() {}

    public enum Action { STATUS, CREATE_PASSWORD, PASSWORD, PIN, CREATE_PIN }
    public enum Result { READY, SIGNED_IN, INCORRECT, INVALID, WAIT, UNAVAILABLE }

    public record Request(int id, GlobalPos pc, Action action, String secret, String pin) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                UltimateBankingSystem.MODID, "pc_login_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::id,
                GlobalPos.STREAM_CODEC, Request::pc,
                ByteBufCodecs.idMapper(index -> Action.values()[index], Action::ordinal), Request::action,
                ByteBufCodecs.stringUtf8(64), Request::secret,
                ByteBufCodecs.stringUtf8(8), Request::pin,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "PcLoginRequest[id=" + id + ", action=" + action + "]";
        }
    }

    public record Response(int id, Result result, boolean passwordSet, boolean pinSet) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                UltimateBankingSystem.MODID, "pc_login_response"));
        public static final StreamCodec<ByteBuf, Response> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Response::id,
                ByteBufCodecs.idMapper(index -> Result.values()[index], Result::ordinal), Response::result,
                ByteBufCodecs.BOOL, Response::passwordSet,
                ByteBufCodecs.BOOL, Response::pinSet,
                Response::new);

        @Override
        public Type<Response> type() {
            return TYPE;
        }
    }
}
