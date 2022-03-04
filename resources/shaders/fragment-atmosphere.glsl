#version 410 core
uniform vec3 light;
in VS_OUT
{
  highp vec3 direction;
  highp vec3 origin;
} fs_in;
out lowp vec3 fragColor;
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light)), 500);
  fragColor = vec3(glare, glare, glare);
}
