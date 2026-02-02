package com.gs.payment.plugin.manager;

public interface SocketCallback {
    void onConnectionStatusChanged(boolean isConnected);
    void onMessageReceived(String message);
    void onError(Exception e);
}