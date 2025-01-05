package spireCafe.interactables.merchants.packmaster;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.*;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.CharacterStrings;
import spireCafe.Anniv7Mod;
import spireCafe.abstracts.AbstractMerchant;
import spireCafe.interactables.merchants.CardArticle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PackmasterMerchant extends AbstractMerchant {
    public static final String ID = PackmasterMerchant.class.getSimpleName();
    public static final CharacterStrings packmasterStrings = CardCrawlGame.languagePack.getCharacterString(Anniv7Mod.makeID(ID));
    private static final Texture BG_TEXTURE = ImageMaster.loadImage("images/npcs/rug/eng.png");

    private static final float TOP_ROW_Y = 760.0F * Settings.yScale;
    private static final float BOTTOM_ROW_Y = 337.0F * Settings.yScale;
    private static final float DRAW_START_X = Settings.WIDTH * 0.16F;

    private static Class<?> packmasterSkin;
    private static Class<?> abstractSkin;
    private static Class<?> anniv5;
    private static Class<?> abstractCardPack;

    private float speechTimer;
    private int shopType;

    public PackmasterMerchant(float animationX, float animationY) {
        super(animationX, animationY, 360.0f, 235.0f);
        this.name = packmasterStrings.NAMES[0];
        this.authors = "modargo";
        this.background = new TextureRegion(BG_TEXTURE);

        Object skin = null;
        String jsonPath = null;
        String atlasPath = null;
        if (Loader.isModLoaded("anniv5")) {
            try {
                packmasterSkin = Class.forName("thePackmaster.skins.instances.PackmasterSkin");
                abstractSkin = Class.forName("thePackmaster.skins.AbstractSkin");
                Constructor<?> constructor = packmasterSkin.getDeclaredConstructor();
                constructor.setAccessible(true);
                skin = constructor.newInstance();
                Method m1 = abstractSkin.getDeclaredMethod("getSkeletonJSONPath");
                Method m2 = abstractSkin.getDeclaredMethod("getSkeletonAtlasPath");
                jsonPath = (String)m1.invoke(skin);
                atlasPath = (String)m2.invoke(skin);

                anniv5 = Class.forName("thePackmaster.SpireAnniversary5Mod");
                abstractCardPack = Class.forName("thePackmaster.packs.AbstractCardPack");
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                throw new RuntimeException("Error retrieving classes from Packmaster", e);
            }

            this.atlas = new TextureAtlas(Gdx.files.internal(atlasPath));
            SkeletonJson json = new SkeletonJson(this.atlas);
            json.setScale(Settings.renderScale);
            SkeletonData skeletonData = json.readSkeletonData(Gdx.files.internal(jsonPath));
            this.skeleton = new Skeleton(skeletonData);
            this.skeleton.setColor(Color.WHITE);
            this.stateData = new AnimationStateData(skeletonData);
            this.state = new AnimationState(this.stateData);
            AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
            e.setTime(e.getEndTime() * MathUtils.random());
        }
    }

    @Override
    public boolean canSpawn() {
        return Loader.isModLoaded("anniv5");
    }

    @Override
    public void onInteract() {
        super.onInteract();
        this.speechTimer = MathUtils.random(5.0f, 10.0f);
    }

    @Override
    public void rollShop() {
        this.shopType = AbstractDungeon.miscRng.random(2);
        ArrayList<Object> allPacks = new ArrayList<>(ReflectionHacks.getPrivateStatic(anniv5, "allPacks"));
        Collections.shuffle(allPacks, new java.util.Random(AbstractDungeon.miscRng.randomLong()));
        ArrayList<AbstractCard> cards = new ArrayList<>();
        switch (this.shopType) {
            case 0:
                cards.addAll(this.getPackCards(allPacks.get(0)));
                break;
            case 1:
                int roll = AbstractDungeon.miscRng.random(2);
                AbstractCard.CardRarity rarity = roll == 0 ? AbstractCard.CardRarity.COMMON : roll == 1 ? AbstractCard.CardRarity.UNCOMMON : AbstractCard.CardRarity.RARE;
                cards.addAll(this.getPackCards(allPacks.get(0)));
                cards.addAll(this.getPackCards(allPacks.get(1)));
                cards.removeIf(c -> c.rarity != rarity);
                break;
            case 2:
                for (int i = 0; i < 10; i++) {
                    List<AbstractCard> rares = this.getPackCards(allPacks.get(i)).stream().filter(c -> c.rarity == AbstractCard.CardRarity.RARE).collect(Collectors.toList());
                    cards.add(rares.get(AbstractDungeon.miscRng.random(rares.size() - 1)));
                }
                break;
        }

        int tmp = (int)(Settings.WIDTH - DRAW_START_X * 2.0F - AbstractCard.IMG_WIDTH_S * 5.0F) / 4;
        float padX = (int)(tmp + AbstractCard.IMG_WIDTH_S) + 10.0F * Settings.scale;
        for (int i = 0; i < Math.min(cards.size(), 10); i++) {
            float xPos = DRAW_START_X + AbstractCard.IMG_WIDTH_S / 2.0F + padX * (i % 5);
            float yPos = i < 5 ? TOP_ROW_Y : BOTTOM_ROW_Y;
            AbstractCard card = cards.get(i);
            int price = (int)(AbstractCard.getPrice(card.rarity) * AbstractDungeon.miscRng.random(0.9f, 1.1f));
            articles.add(new CardArticle(card.cardID, this, xPos, yPos, card, price));
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayList<AbstractCard> getPackCards(Object pack) {
        try {
            ArrayList<AbstractCard> cards = new ArrayList<>();
            for (AbstractCard card : (ArrayList<AbstractCard>)ReflectionHacks.getCachedField(abstractCardPack, "cards").get(pack)) {
                if (card.rarity == AbstractCard.CardRarity.COMMON || card.rarity == AbstractCard.CardRarity.UNCOMMON || card.rarity == AbstractCard.CardRarity.RARE) {
                    cards.add(card);
                }
            }
            return cards;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateShop() {
        super.updateShop();
        this.speechTimer -= Gdx.graphics.getDeltaTime();
        if (this.speechBubble == null && this.speechTimer <= 0.0f) {
            this.speechTimer = MathUtils.random(40.0f, 60.0f);
            this.createSpeechBubble(this.getRandomMessage());
        }
    }

    private String getRandomMessage() {
        String[] s = packmasterStrings.TEXT;
        ArrayList<String> possibleMessages = new ArrayList<>();
        int baseMessageCount = 6;
        for (int i = 0; i < baseMessageCount; i++) {
            possibleMessages.add(s[i]);
        }
        if (AbstractDungeon.player.chosenClass.toString().equals("THE_PACKMASTER")) {
            possibleMessages.add(s[5]);
        }
        possibleMessages.add(s[baseMessageCount + 1 + this.shopType]);

        return possibleMessages.get(MathUtils.random(possibleMessages.size() - 1));
    }

    @Override
    public void onCloseShop() {
    }
}
