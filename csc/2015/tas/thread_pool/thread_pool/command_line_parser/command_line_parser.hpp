#ifndef COMMAND_LINEPARSER_HPP_INCLUDED
#define COMMAND_LINEPARSER_HPP_INCLUDED

class CommandLineParser
{
public:
    int count_threads{ 0 };

    bool parse(int argc, char *argv[]);
};

#endif
