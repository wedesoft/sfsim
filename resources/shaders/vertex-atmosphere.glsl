#version 410 core
uniform mat4 projection;
uniform mat4 inverse_transform;
in highp vec3 point;
out VS_OUT
{
  highp vec3 direction;
  highp vec3 origin;
} vs_out;
void main()
{
  vs_out.direction = point;
  vs_out.origin = vec3(0, 0, 0);
  gl_Position = projection * vec4(point, 1);
}
