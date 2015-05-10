#ifndef LOG
#define LOG

#include <boost/thread/recursive_mutex.hpp>
#include <boost/thread/lock_types.hpp>

#include <ostream>
#include <utility>

class log {
public:
    template <class Arg, class... Args>
    static void cerr(Arg&& arg, Args&&... args)
    {
        write(std::cerr, std::forward<Arg>(arg), std::forward<Args>(args)...);
    }

    template <class Arg, class... Args>
    static void cout(Arg&& arg, Args&&... args)
    {
        write(std::cout, std::forward<Arg>(arg), std::forward<Args>(args)...);
    }

    template <class Arg>
    static void cout(Arg&& arg)
    {
        write(std::cout, std::forward<Arg&&>(arg));
    }

    template <class Arg>
    static void cerr(Arg&& arg)
    {
        write(std::cerr, std::forward<Arg&&>(arg));
    }

private:
    static boost::recursive_mutex log_mutex;

    template <class Arg>
    static void write(std::ostream& stream, Arg&& arg)
    {
        boost::unique_lock<boost::recursive_mutex> guard(log::log_mutex);
        stream << arg << std::endl;
    }

    template <class Arg, class... Args>
    static void write(std::ostream& stream, Arg&& arg, Args&&... args)
    {
        boost::unique_lock<boost::recursive_mutex> guard(log::log_mutex);
        stream << arg << " ";
        write(stream, args...);
    }
};

#endif // LOG

