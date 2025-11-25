#version 450 core

float limit_quot(float a, float b, float lower, float upper)
{
  if (a == 0.0)
    return 0.0;
  if (b < 0) {
    a = -a;
    b = -b;
  };
  if (a < b * upper) {
    if (a > b * lower)
      return a / b;
    else
      return lower;
  } else
    return upper;
}
