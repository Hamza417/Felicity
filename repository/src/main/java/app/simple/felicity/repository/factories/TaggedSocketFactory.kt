package app.simple.felicity.repository.factories

import android.net.TrafficStats
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class TaggedSocketFactory(private val tag: Int) : SocketFactory() {
    private val delegate = getDefault()

    override fun createSocket(): Socket = tagSocket(delegate.createSocket())

    override fun createSocket(host: String, port: Int): Socket =
        tagSocket(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        tagSocket(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        tagSocket(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        tagSocket(delegate.createSocket(address, port, localAddress, localPort))

    private fun tagSocket(socket: Socket): Socket {
        /**
         * We have to set the thread-level tag before calling tagSocket, otherwise Android
         * treats the socket as untagged even though we're going through the tagging flow.
         * The tag value comes from whoever built this factory, so different HTTP clients
         * can be tracked separately in network usage stats.
         */
        TrafficStats.setThreadStatsTag(tag)
        TrafficStats.tagSocket(socket)
        return socket
    }
}