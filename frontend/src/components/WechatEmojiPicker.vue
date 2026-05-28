<template>
  <div class="emoji-panel" @click.stop>
    <div class="emoji-content">
      <template v-if="activePack === 'emoji'">
        <h4>最近使用</h4>
        <div class="emoji-grid emoji-10">
          <span v-for="e in recentEmoji" :key="'r' + e" class="emoji" @click="selectEmoji(e)">{{ e }}</span>
        </div>

        <h4>全部表情</h4>
        <div class="emoji-grid emoji-10">
          <span v-for="e in allEmoji" :key="e" class="emoji" @click="selectEmoji(e)">{{ e }}</span>
        </div>
      </template>
      <template v-if="activePack === 'favorite'">
        <div class="sticker-grid">
          <div v-for="item in favoriteStickers" :key="item.id" class="sticker-item" @click="sendSticker(item)">
            <img :src="item.url" />
            <span>{{ item.name }}</span>
          </div>

          <div class="sticker-item add" @click="openAdd">➕</div>
        </div>
      </template>
    </div>
    <div class="emoji-footer">
      <div v-for="pack in packs" :key="pack.key" class="pack-item" :class="{ active: activePack === pack.key }"
        @click="activePack = pack.key">
        {{ pack.icon }}
      </div>
    </div>
    <input ref="stickerInput" type="file" accept="image/*" style="display:none" @change="onStickerFile" />
  </div>
</template>
<script setup>
import { ref } from "vue";

const emit = defineEmits(["select", "send-sticker"]);

const activePack = ref("emoji");
const search = ref("");

const packs = [
  { key: "emoji", icon: "😀" },
  { key: "favorite", icon: "❤️" },
];

const allEmoji = [
  "😀",
  "😃",
  "😄",
  "😁",
  "😆",
  "😅",
  "😂",
  "🤣",
  "😊",
  "😇",
  "🙂",
  "🙃",
  "😉",
  "😌",
  "😍",
  "🥰",
  "😘",
  "😗",
  "😙",
  "😚",
  "😋",
  "😛",
  "😜",
  "🤪",
  "😝",
  "🤑",
  "🤗",
  "🤭",
  "🤫",
  "🤔",
  "🤐",
  "🤨",
  "😐",
  "😑",
  "😶",
  "😏",
  "😒",
  "🙄",
  "😬",
  "🤥",
  "😌",
  "😔",
  "😪",
  "🤤",
  "😴",
  "😷",
  "🤒",
  "🤕",
  "🤢",
  "🤮",
  "🤧",
  "🥵",
  "🥶",
  "🥴",
  "😵",
  "🤯",
  "🤠",
  "🥳",
  "😎",
  "🤓",
  "🧐",
  "😕",
  "😟",
  "🙁",
  "☹️",
  "😮",
  "😯",
  "😲",
  "😳",
  "🥺",
  "😦",
  "😧",
  "😨",
  "😰",
  "😥",
  "😢",
  "😭",
  "😱",
  "😖",
  "😣",
  "😞",
  "😓",
  "😩",
  "😫",
  "🥱",
  "😤",
  "😡",
  "😠",
  "🤬",
  "😈",
  "👿",
  "💀",
  "☠️",
  "💩",
  "🤡",
  "👹",
  "👺",
  "👻",
  "👽",
  "👾",
  "🤖",
  "😺",
  "😸",
  "😹",
  "😻",
  "😼",
  "😽",
  "🙀",
  "😿",
  "😾",
  "👶",
  "🧒",
  "👦",
  "👧",
  "🧑",
  "👱‍♂️",
  "👱‍♀️",
  "👨",
  "👩",
  "🧔",
  "👴",
  "👵",
  "🙍‍♂️",
  "🙍‍♀️",
  "🙎‍♂️",
  "🙎‍♀️",
  "🙅‍♂️",
  "🙅‍♀️",
  "🙆‍♂️",
  "🙆‍♀️",
  "💁‍♂️",
  "💁‍♀️",
  "🙋‍♂️",
  "🙋‍♀️",
  "🤦‍♂️",
  "🤦‍♀️",
  "🤷‍♂️",
  "🤷‍♀️",
  "🧏‍♂️",
  "🧏‍♀️",
  "🙇‍♂️",
  "🙇‍♀️",
  "💆‍♂️",
  "💆‍♀️",
  "💇‍♂️",
  "💇‍♀️",
  "🚶‍♂️",
  "🚶‍♀️",
  "🏃‍♂️",
  "🏃‍♀️",
  "💃",
  "🕺",
  "🕴️",

];

const recentEmoji = ref(["😀", "😂", "😍"]);

const favoriteStickers = ref([
  {
    id: 2,
    name: "笑哭",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Face%20with%20tears%20of%20joy/3D/face_with_tears_of_joy_3d.png",
  },
  {
    id: 3,
    name: "色",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Smiling%20face%20with%20heart-eyes/3D/smiling_face_with_heart-eyes_3d.png",
  },
  {
    id: 4,
    name: "酷",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Smiling%20face%20with%20sunglasses/3D/smiling_face_with_sunglasses_3d.png",
  },
  {
    id: 5,
    name: "思考",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Thinking%20face/3D/thinking_face_3d.png",
  },
  {
    id: 6,
    name: "大哭",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Loudly%20crying%20face/3D/loudly_crying_face_3d.png",
  },
  {
    id: 7,
    name: "炸裂",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Exploding%20head/3D/exploding_head_3d.png",
  },
  {
    id: 11,
    name: "心火",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Heart%20on%20fire/3D/heart_on_fire_3d.png",
  },
  {
    id: 13,
    name: "庆祝",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Partying%20face/3D/partying_face_3d.png",
  },
  {
    id: 14,
    name: "火了",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Fire/3D/fire_3d.png",
  },
  {
    id: 15,
    name: "起飞",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Rocket/3D/rocket_3d.png",
  },
  {
    id: 16,
    name: "彩带",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Party%20popper/3D/party_popper_3d.png",
  },
  {
    id: 17,
    name: "奖杯",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Trophy/3D/trophy_3d.png",
  },
  {
    id: 18,
    name: "彩虹",
    url: "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Rainbow/3D/rainbow_3d.png",
  },
]);
const selectEmoji = (emoji) => {
  emit("select", { native: emoji });
  if (!recentEmoji.value.includes(emoji)) {
    recentEmoji.value.unshift(emoji);
  }
};

const sendSticker = (sticker) => {
  emit("send-sticker", sticker);
};

const stickerInput = ref(null);
const openAdd = () => {
  stickerInput.value?.click();
};
const onStickerFile = (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (ev) => {
    const newSticker = {
      id: Date.now(),
      name: file.name.replace(/\.[^.]+$/, ''),
      url: ev.target.result,
      file: file
    };
    favoriteStickers.value.unshift(newSticker);
  };
  reader.readAsDataURL(file);
  e.target.value = '';
};
</script>
<style>
.emoji-panel {
  position: absolute;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

.emoji-panel::before {
  content: "";
  position: absolute;
  bottom: -15px;
  left: 217px;
  width: 0;
  height: 0;
  border-left: 16px solid transparent;
  border-right: 16px solid transparent;
  border-top: 14px solid rgba(0, 0, 0, 0.15);
}

.emoji-panel::after {
  content: "";
  position: absolute;
  bottom: -14px;
  left: 218px;
  width: 0;
  height: 0;
  border-left: 14px solid transparent;
  border-right: 14px solid transparent;
  border-top: 14px solid #fff;
}

.emoji-panel {
  width: 470px;
  height: 500px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}

.emoji-pack-bar {
  display: flex;
  padding: 6px;
  border-bottom: 1px solid #eee;
}

.pack-item {
  width: 36px;
  height: 36px;
  margin-right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.pack-item.active {
  background: #f0f0f0;
  border-radius: 6px;
}

.emoji-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

h4 {
  margin: 8px 0;
  font-size: 13px;
  color: #666;
}

.emoji-grid.emoji-10 {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
}

.emoji {
  font-size: 24px;
  cursor: pointer;
}

.emoji:hover {
  background: #f0f0f0;
  border-radius: 4px;
}

.sticker-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.sticker-item {
  text-align: center;
  cursor: pointer;
}

.sticker-item img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
}

.sticker-item.add {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  border: 1px dashed #bbb;
  border-radius: 6px;
}

.emoji-footer {
  height: 50px;
  display: flex;
  align-items: center;
  border-top: 1px solid #eee;
  padding: 0 6px;
}

.emoji-search {
  flex: 1;
  margin-right: 8px;
}

.footer-icon {
  width: 36px;
  text-align: center;
  font-size: 20px;
  cursor: pointer;
}

.footer-icon.active {
  color: #409eff;
}
</style>
