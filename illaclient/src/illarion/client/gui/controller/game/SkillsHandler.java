/*
 * This file is part of the Illarion Client.
 *
 * Copyright © 2012 - Illarion e.V.
 *
 * The Illarion Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Illarion Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Illarion Client.  If not, see <http://www.gnu.org/licenses/>.
 */
package illarion.client.gui.controller.game;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.controls.label.builder.LabelBuilder;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.events.ElementShowEvent;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.SizeValue;
import illarion.client.net.server.events.SkillReceivedEvent;
import illarion.client.util.Lang;
import illarion.common.data.Skill;
import illarion.common.data.SkillGroup;
import illarion.common.data.SkillGroups;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.newdawn.slick.GameContainer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This handler controls the skill window.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
public final class SkillsHandler implements ScreenController, UpdatableHandler {
    /**
     * The Nifty-GUI instance this handler is bound to.
     */
    private Nifty nifty;

    /**
     * The screen instance this handler is bound to.
     */
    private Screen screen;

    /**
     * The window control that contains the skill information.
     */
    private Window skillWindow;

    /**
     * The Queue of updates that need to be executed for the GUI..
     */
    private final Queue<Runnable> updateQueue;

    /**
     * The default constructor.
     */
    public SkillsHandler() {
        updateQueue = new ConcurrentLinkedQueue<Runnable>();
    }

    @Override
    public void bind(final Nifty nifty, final Screen screen) {
        this.nifty = nifty;
        this.screen = screen;

        skillWindow = screen.findNiftyControl("characterInformation", Window.class);

        createSkillEntries();
    }

    @Override
    public void onStartScreen() {
        nifty.subscribeAnnotations(this);
        AnnotationProcessor.process(this);
    }

    @Override
    public void onEndScreen() {
        nifty.unsubscribeAnnotations(this);
        AnnotationProcessor.unprocess(this);
    }

    public void showSkillWindow() {
        if (skillWindow != null) {
            skillWindow.getElement().show();
        }
    }

    public void hideSkillWindow() {
        if (skillWindow != null) {
            skillWindow.getElement().hide();
        }
    }

    public void toggleSkillWindow() {
        if (skillWindow != null) {
            if (skillWindow.getElement().isVisible()) {
                hideSkillWindow();
            } else {
                showSkillWindow();
            }
        }
    }

    /**
     * This function creates the entries for every single skill.
     */
    private void createSkillEntries() {
        final Element content = skillWindow.getElement().findElementByName("#textContent");

        int groupCnt = 0;
        for (final SkillGroup group : SkillGroups.getInstance().getSkillGroups()) {
            final String groupId = content.getId() + "#group" + Integer.toString(groupCnt++);
            final PanelBuilder groupPanel = new PanelBuilder(groupId);
            groupPanel.childLayoutVertical();

            final LabelBuilder headline = new LabelBuilder(groupId + "#headline");
            headline.font("menuFont");

            if (Lang.getInstance().isGerman()) {
                headline.label(group.getNameGerman());
            } else {
                headline.label(group.getNameEnglish());
            }
            headline.width("*");
            headline.height("0px");
            groupPanel.control(headline);

            for (final Skill skill : group.getSkills()) {
                final String skillId = groupId + "#skill" + Integer.toString(skill.getId());
                final PanelBuilder skillPanel = new PanelBuilder(skillId);
                skillPanel.childLayoutCenter();
                skillPanel.width(content.getConstraintWidth().toString());

                final LabelBuilder skillName = new LabelBuilder(skillId + "#name");
                if (Lang.getInstance().isGerman()) {
                    skillName.label(skill.getNameGerman());
                } else {
                    skillName.label(skill.getNameEnglish());
                }
                skillName.font("textFont");
                skillName.width(content.getConstraintWidth().toString());
                skillName.height("0px");
                skillName.alignLeft();
                skillName.textHAlignLeft();
                skillPanel.control(skillName);

                final LabelBuilder skillValue = new LabelBuilder(skillId + "#value");
                skillValue.label("0");
                skillValue.width(content.getConstraintWidth().toString());
                skillValue.font("textFont");
                skillValue.height("0px");
                skillValue.alignRight();
                skillValue.textHAlignRight();
                skillPanel.control(skillValue);

                groupPanel.panel(skillPanel);
            }

            groupPanel.build(nifty, screen, content);
        }
    }

    /**
     * This value is set {@code true} in case the layout needs to be updated.
     */
    private boolean layoutDirty;

    /**
     * This function will update the data of a single skill.
     *
     * @param updateData the information needed to perform the update
     */
    private void updateSkill(final SkillReceivedEvent updateData) {
        final Element skillPanel = skillWindow.getElement().findElementByName("#skill" +
                Integer.toString(updateData.getSkill().getId()));

        if (updateData.getValue() == 0) {
            skillPanel.setConstraintHeight(SizeValue.px(0));
        } else {
            skillPanel.getParent().setConstraintHeight(SizeValue.wildcard());
            skillPanel.getParent().setMarginBottom(SizeValue.px(5));
            skillPanel.getParent().findElementByName("#headline").setConstraintHeight(SizeValue.px(24));
            skillPanel.setConstraintHeight(SizeValue.px(18));

            final Element valueLabel = skillPanel.findElementByName("#value");
            final TextRenderer valueTextRenderer = valueLabel.getRenderer(TextRenderer.class);
            valueTextRenderer.setText(Integer.toString(updateData.getValue()));
            valueLabel.setConstraintHeight(SizeValue.px(18));

            final Element nameLabel = skillPanel.findElementByName("#name");
            nameLabel.setConstraintHeight(SizeValue.px(18));
            nameLabel.setMarginLeft(SizeValue.px(5));
        }

        layoutDirty = true;
    }

    private void updateVisibility() {
        final Element content = skillWindow.getElement().findElementByName("#textContent");
        updateVisibilityOfElement(content);
    }

    private void updateVisibilityOfElement(final Element target) {
        if ("0px".equals(target.getConstraintHeight().toString())) {
            target.setVisible(false);
        } else {
            for (final Element child : target.getElements()) {
                updateVisibilityOfElement(child);
            }
        }
    }

    @Override
    public void update(final GameContainer container, final int delta) {
        while (true) {
            final Runnable task = updateQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }

        if (layoutDirty) {
            layoutDirty = false;

            skillWindow.getElement().layoutElements();
        }
    }

    /**
     * The event handler for new skills that are received from the server.
     *
     * @param data the skill data
     */
    @EventSubscriber
    public void onSkillUpdateReceived(final SkillReceivedEvent data) {
        updateQueue.add(new Runnable() {
            @Override
            public void run() {
                updateSkill(data);
            }
        });
    }

    /**
     * The event handler for clicks on the skill window button.
     *
     * @param topic the topic of the event
     * @param event the event data
     */
    @NiftyEventSubscriber(id = "openSkillsBtn")
    public void onSkillWindowButtonClickedEvent(final String topic, final ButtonClickedEvent event) {
        toggleSkillWindow();
    }

    @NiftyEventSubscriber(id = "characterInformation")
    public void onSkillWindowShowEvent(final String topic, final ElementShowEvent event) {
        updateVisibility();
    }
}
