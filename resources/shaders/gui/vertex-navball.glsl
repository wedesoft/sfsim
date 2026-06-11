#version 130

in vec3 point;

void main()
{
  gl_Position = vec4(point, 1);
}
