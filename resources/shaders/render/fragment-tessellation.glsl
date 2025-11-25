#version 450 core

in float color;

out vec4 fragColor;

void main()
{
  fragColor = vec4(color, color, color, 1.0);
}
