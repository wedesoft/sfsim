#version 410 core
uniform sampler2D colors;
uniform sampler2D normals;
uniform vec3 light;
in GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
} fs_in;
out lowp vec3 fragColor;
void main()
{
  vec3 normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 color = texture(colors, fs_in.colorcoord).rgb;
  float cos_incidence = max(dot(light, normal), 0);
  fragColor = color * cos_incidence;
}
