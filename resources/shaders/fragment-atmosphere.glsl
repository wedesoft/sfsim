#version 410 core
in VS_OUT
{
  highp vec3 direction;
  highp vec3 origin;
} fs_in;
out lowp vec3 fragColor;
void main()
{
  vec3 light = vec3(0, 0, -1);
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light)), 500);
  fragColor = vec3(glare, glare, glare);
}
