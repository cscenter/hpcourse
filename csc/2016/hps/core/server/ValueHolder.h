#ifndef __VALUE_HOLDER__
#define __VALUE_HOLDER__

#include <iostream>

template<class T>                                                                                               
struct ValueHolder                                                                                              
{                                                                                                               
    ValueHolder() : m_empty(true) { }                                                                           
    ValueHolder(const T & data) : m_data(data), m_empty(false) { }                                              
    T & get() const { m_empty = false; return m_data; }                                                               
    operator bool() const { return !m_empty; }                                                                  
    friend std::ostream & operator<<(std::ostream & s, const ValueHolder<T> & v) {
        if (v) {                                                                                                
            return s << v.m_data;                                                                               
        } else {                                                                                                
            return s << "empty";                                                                                
        }                                                                                                       
    }                                                                                                           
    private:                                                                                                    
        mutable T m_data;                                                                                               
        mutable bool m_empty;                                                                                           
};

#endif //__VALUE_HOLDER__
