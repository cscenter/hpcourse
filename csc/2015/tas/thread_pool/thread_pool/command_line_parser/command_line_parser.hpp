#ifndef COMMAND_LINEPARSER_HPP_INCLUDED
#define COMMAND_LINEPARSER_HPP_INCLUDED

class CommandLineParser
{
public:
    bool parse(int argc, char *argv[]);

    int count_threads{ 0 };
};

#endif
