from langchain.memory import ConversationBufferMemory

def get_buffer_memory(memory_key="chat_history", return_messages=True):
    """
    获取对话缓存 Memory
    """
    return ConversationBufferMemory(
        memory_key=memory_key,
        return_messages=return_messages
    )
