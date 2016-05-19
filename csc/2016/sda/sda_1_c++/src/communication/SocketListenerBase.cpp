#include "SocketListenerBase.hpp"

namespace communication {

SocketListenerBase::SocketListenerBase(io_service & service) :
    m_sock(service),
    m_started(false)
{}

void SocketListenerBase::start() {
    m_started = true;
    read();
}

void SocketListenerBase::start(ip::tcp::endpoint ep) {
    m_sock.async_connect(ep, MEM_FN1(on_connect,_1));
}

void SocketListenerBase::stop() {
    if ( !m_started )
        return;
    m_started = false;
    m_sock.close();
}

ip::tcp::socket & SocketListenerBase::sock() {
    return m_sock;
}

void SocketListenerBase::set_message(const std::string & msg) {
    m_message = msg;
}

void SocketListenerBase::on_connect(const error_code & err) {
    if ( !err ) {
        if (m_message.size())
            write(m_message);
        else
            std::cerr << "WARNING: no message" << std::endl;
    } else {
        stop();
    }
}

void SocketListenerBase::on_write_end(const error_code & err, size_t bytes) {
    read();
}

void SocketListenerBase::read() {
    async_read(m_sock, buffer(m_read_buffer),
               MEM_FN2(read_complete,_1,_2), MEM_FN2(on_read_end,_1,_2));
}

void SocketListenerBase::write(const std::string & msg) {
    unsigned int n = msg.size();

    unsigned char bytes[4];
    for (int i = 0; i < 4; ++i) {
        bytes[i] = (n >> (24 - i * 8)) & 0xFF;
    }

    std::string str_to_send(bytes, bytes + 4);
    str_to_send += msg;


    std::copy(str_to_send.begin(), str_to_send.end(), m_write_buffer);
    m_sock.async_write_some(buffer(m_write_buffer, str_to_send.size()),
                            MEM_FN2(on_write_end,_1,_2));
}

size_t SocketListenerBase::read_complete(const error_code & err, size_t bytes) {
    if ( err ) {
        return 0;
    }

    if ( m_len == 0 && bytes == 4 ) {
        for (int i = 0; i < 4; ++i) {
            m_len |= m_read_buffer[i] << (24 - i * 8);
        }
    }

    return m_len == bytes - 4 ? 0 : 1;
}

} // namespace communication
