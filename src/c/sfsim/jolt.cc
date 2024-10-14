#include <Jolt/Jolt.h>

extern "C" {
  void RegisterDefaultAllocator(void);
}

void RegisterDefaultAllocator(void)
{
  JPH::RegisterDefaultAllocator();
}
