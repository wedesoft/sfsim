#version 450 core

uniform sampler2D tex;

in vec2 frag_uv;
in vec4 frag_color;
out vec4 fragColor;

void main()
{
  fragColor = frag_color * texture(tex, frag_uv);
}
